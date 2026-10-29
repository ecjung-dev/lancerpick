"""Check guard expressions with SQLite's SQL three-valued logic, not MySQL DDL.

Only the order comparisons and TEXT length expressions are evaluated. This does
not test MySQL types, collation, FKs, migrations, locking or constraint execution.
"""
from pathlib import Path
from contextlib import closing
import re
import sqlite3
import unittest

SQL = (Path(__file__).resolve().parents[1] / "sql" / "V1__init.sql").read_text(encoding="utf-8")
TABLES = dict(re.findall(r"(?ms)^CREATE TABLE (\w+) \(\n(.*?)^\) ENGINE=", SQL))


def checks(body):
    result = {}
    for match in re.finditer(r"CONSTRAINT (\w+) CHECK \(", body):
        start, depth, quote = match.end(), 1, False
        for end in range(start, len(body)):
            char = body[end]
            if char == "'":
                quote = not quote
            elif not quote:
                depth += (char == "(") - (char == ")")
            if depth == 0:
                result[match[1]] = body[start:end]
                break
        else:
            raise ValueError("Unclosed CHECK expression")
    return result


def permits(expression, row):
    with closing(sqlite3.connect(":memory:")) as connection:
        connection.create_function("CHAR_LENGTH", 1, lambda s: len(s) if s is not None else None)
        columns = ", ".join(f'? AS "{name}"' for name in row)
        value = connection.execute(
            f"SELECT ({expression}) FROM (SELECT {columns})", list(row.values())).fetchone()[0]
    # A SQL CHECK rejects FALSE; TRUE and UNKNOWN are accepted.
    return value != 0


def order(status="REQUESTED", accepted=False, paid=False, completed=False, cancelled=False):
    return dict(
        id=1, buyer_id=1, seller_id=2, product_id=1, request_key="00000000-0000-0000-0000-000000000001",
        status=status, price_amount=50000, duration_days_snapshot=1,
        created_at="2026-10-29 10:00:00", updated_at="2026-10-29 16:00:00",
        accepted_at="2026-10-29 11:00:00" if accepted else None,
        buyer_confirmed_terms_at="2026-10-29 12:00:00" if paid else None,
        paid_at="2026-10-29 12:00:00" if paid else None,
        due_at="2026-10-30 12:00:00" if paid else None,
        completed_at="2026-10-29 14:00:00" if completed else None,
        cancelled_at="2026-10-29 15:00:00" if cancelled else None,
    )


def rejected_checks(row):
    return [name for name, expr in checks(TABLES["orders"]).items() if not permits(expr, row)]


class SchemaCheckExpressionTests(unittest.TestCase):
    def test_valid_states_and_all_three_cancellation_paths(self):
        rows = [order(), order("REJECTED"), order("AWAITING_PAYMENT", accepted=True)]
        rows += [order(s, accepted=True, paid=True) for s in
                 ("IN_PROGRESS", "AWAITING_CONFIRMATION", "REVISION_IN_PROGRESS")]
        rows += [order("COMPLETED", accepted=True, paid=True, completed=True)]
        rows += [order("CANCELLED", accepted=a, paid=p, cancelled=True)
                 for a, p in ((False, False), (True, False), (True, True))]
        for row in rows:
            with self.subTest(status=row["status"], paid=row["paid_at"], accepted=row["accepted_at"]):
                self.assertEqual([], rejected_checks(row))

    def test_inconsistent_state_and_event_presence_is_rejected(self):
        rows = [order("AWAITING_PAYMENT"), order("REQUESTED", accepted=True),
                order("REQUESTED", accepted=True, paid=True),
                order("REJECTED", accepted=True, paid=True),
                order("AWAITING_PAYMENT", accepted=True, paid=True),
                order("IN_PROGRESS", accepted=True), order("COMPLETED", completed=True)]
        for row in rows:
            with self.subTest(status=row["status"], paid=row["paid_at"]):
                self.assertTrue(rejected_checks(row))

    def test_reversed_event_times_are_rejected(self):
        changes = [
            ("accepted_at", "2026-10-29 09:00:00"),
            ("buyer_confirmed_terms_at", "2026-10-29 10:30:00"),
            ("paid_at", "2026-10-29 11:30:00"),
            ("due_at", "2026-10-29 11:59:59"),
            ("due_at", "2026-10-29 12:00:00"),
            ("completed_at", "2026-10-29 11:59:59"),
            ("updated_at", "2026-10-29 13:00:00"),
        ]
        for field, value in changes:
            row = order("COMPLETED", accepted=True, paid=True, completed=True)
            row[field] = value
            with self.subTest(field=field, value=value):
                self.assertTrue(rejected_checks(row))
        for accepted, paid, too_early in ((False, False, "2026-10-29 09:00:00"),
                                          (True, False, "2026-10-29 10:30:00"),
                                          (True, True, "2026-10-29 11:30:00")):
            row = order("CANCELLED", accepted=accepted, paid=paid, cancelled=True)
            row["cancelled_at"] = too_early
            self.assertTrue(rejected_checks(row))

    def test_each_text_field_rejects_trailing_space_length_bypass(self):
        tested = 0
        for table, body in TABLES.items():
            for match in re.finditer(r"(?m)^    (\w+) TEXT (NOT NULL|NULL)", body):
                column, nullability = match.groups()
                candidates = [e for e in checks(body).values()
                              if re.search(r"CHAR_LENGTH\(" + re.escape(column) + r"\)", e)]
                self.assertEqual(1, len(candidates), f"Missing raw-length guard: {table}.{column}")
                expr = candidates[0]
                limit = int(re.search(r"BETWEEN 1 AND (\d+)", expr)[1])
                for value, expected in (("A", True), ("가"*limit, True), ("😀"*limit, True),
                                        ("A"+" "*limit, False), ("가"*(limit+1), False),
                                        ("", False), ("   ", False)):
                    with self.subTest(table=table, column=column, length=len(value)):
                        self.assertEqual(expected, permits(expr, {column: value}))
                if nullability == "NULL":
                    self.assertTrue(permits(expr, {column: None}))
                tested += 1
        self.assertEqual(11, tested)


if __name__ == "__main__":
    unittest.main(verbosity=2)
