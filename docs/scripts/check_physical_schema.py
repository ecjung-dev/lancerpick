"""Static checks for this project's DDL; not a MySQL parser or execution test."""
from pathlib import Path
import argparse
import re

DOCS = Path(__file__).resolve().parents[1]
SQL_PATH = DOCS.parent / "LancerPick" / "src" / "main" / "resources" / "db" / "migration" / "V1__init.sql"
EXPECTED = {
    "members", "seller_profiles", "categories", "products", "orders", "order_snapshots",
    "order_histories", "deliveries", "revision_requests", "cancellation_requests",
    "wallets", "payments", "wallet_entries", "settlements", "reviews",
    "product_moderation_logs",
}
TYPE = r"(?:BIGINT|INT|BOOLEAN|VARCHAR\(\d+\)|CHAR\(\d+\)|TEXT|DATETIME\(6\))"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-dictionary", action="store_true")
    args = parser.parse_args()
    sql = SQL_PATH.read_text(encoding="utf-8")
    code = re.sub(r"(?m)^--.*$", "", sql)
    require(not re.search(r"\b(DROP|TRUNCATE|DELETE|INSERT|UPDATE|USE|CASCADE)\b", code, re.I),
            "DDL must contain only additive schema definitions, without seeds or destructive operations")
    matches = list(re.finditer(
        r"(?ms)^CREATE TABLE (\w+) \(\n(.*?)^\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;", sql))
    require(len(matches) == len(EXPECTED), f"Expected {len(EXPECTED)} CREATE TABLE blocks")
    require({m[1] for m in matches} == EXPECTED, "Tables differ from the logical ERD")
    require(code.count(";") == len(EXPECTED), "Unexpected statements outside CREATE TABLE")
    tables = {}
    constraints = set()
    dictionaries = []
    fk_count = check_count = column_count = 0
    for match in matches:
        name, body = match[1], match[2]
        cols = {}
        for col in re.finditer(rf"(?m)^    (\w+) ({TYPE}) (.+?)(?:,)?$", body):
            cname, ctype, rest = col.groups()
            require(cname not in cols, f"Duplicate column {name}.{cname}")
            require("NOT NULL" in rest or re.search(r"\bNULL\b", rest), f"Missing nullability: {name}.{cname}")
            cols[cname] = (ctype, rest)
        require(cols, f"No columns parsed: {name}")
        primary = re.search(r"PRIMARY KEY \(([^)]+)\)", body)
        require(primary is not None, f"Missing PK: {name}")
        index_lists = [primary[1]] + re.findall(r"(?:UNIQUE KEY|KEY) \w+ \(([^)]+)\)", body)
        indexes = []
        unique_lists = [primary[1]] + re.findall(r"UNIQUE KEY \w+ \(([^)]+)\)", body)
        for listing in index_lists:
            names = [part.strip().split()[0] for part in listing.split(",")]
            require(all(c in cols for c in names), f"Unknown indexed column: {name}: {listing}")
            indexes.append(names)
        for constraint in re.findall(r"CONSTRAINT (\w+)", body):
            require(constraint not in constraints, f"Duplicate schema constraint: {constraint}")
            require(len(constraint) <= 64, f"Constraint name too long: {constraint}")
            constraints.add(constraint)
        for local, parent, remote in re.findall(
                r"FOREIGN KEY \((\w+)\) REFERENCES (\w+) \((\w+)\)", body):
            require(local in cols, f"Unknown FK column: {name}.{local}")
            require(parent in tables, f"Parent must be created first: {name} -> {parent}")
            require(remote in tables[parent]["cols"], f"Unknown referenced column: {parent}.{remote}")
            require(cols[local][0] == tables[parent]["cols"][remote][0], f"FK type mismatch: {name}.{local}")
            require(any(ix[0] == local for ix in indexes), f"Missing FK left-prefix index: {name}.{local}")
            require(remote in tables[parent]["single_unique"], f"Referenced column must be independently unique: {parent}.{remote}")
            fk_count += 1
        tables[name] = {"cols": cols, "body": body, "single_unique": {
            listing.strip() for listing in unique_lists if "," not in listing
        }}
        for cname, (ctype, rest) in cols.items():
            if cname.endswith("_id") or cname == "id":
                require(ctype == "BIGINT", f"ID type must be signed BIGINT: {name}.{cname}")
            if cname.endswith("_at"):
                require(ctype == "DATETIME(6)", f"Timestamp precision mismatch: {name}.{cname}")
        check_count += body.count(" CHECK (")
        column_count += len(cols)
        lines = [f"### {name}", "", "| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |",
                 "| --- | --- | --- | --- | --- |"]
        for cname, (ctype, rest) in cols.items():
            nullable = "불가" if "NOT NULL" in rest else "허용"
            default = re.search(r"\bDEFAULT ('[^']*'|\w+)", rest)
            default_text = "AUTO_INCREMENT" if "AUTO_INCREMENT" in rest else default[1] if default else "없음"
            comment = re.search(r"COMMENT '([^']*)'", rest)
            lines.append(f"| `{cname}` | `{ctype}` | {nullable} | {default_text} | {comment[1] if comment else ''} |")
        dictionaries.append("\n".join(lines))

    required_unique = {
        "members": ["email"], "orders": ["buyer_id, request_key"],
        "deliveries": ["order_id, sequence_no"],
        "revision_requests": ["order_id", "delivery_id"],
        "cancellation_requests": ["order_id, requested_by, request_key"],
        "payments": ["order_id"], "settlements": ["order_id"], "reviews": ["order_id"],
        "wallet_entries": ["event_key", "payment_id, type"],
    }
    for table, listings in required_unique.items():
        for listing in listings:
            require(re.search(r"UNIQUE KEY \w+ \(" + re.escape(listing) + r"\)", tables[table]["body"]),
                    f"Required uniqueness missing: {table}({listing})")
    require(not re.search(r"UNIQUE KEY \w+ \(\s*order_id\s*,\s*status\s*\)", tables["cancellation_requests"]["body"]),
            "Do not prevent repeated resolved cancellation histories")
    snapshot_columns = {
        "product_version_snapshot", "product_title_snapshot", "description_snapshot",
        "scope_snapshot", "preparation_snapshot", "terms_snapshot", "requirements",
    }
    require(set(tables["order_snapshots"]["cols"]) == snapshot_columns | {"order_id"},
            "Snapshot columns differ from the agreed split")
    require(not snapshot_columns.intersection(tables["orders"]["cols"]),
            "Snapshot data must not remain duplicated in orders")
    require({"price_amount", "duration_days_snapshot"}.issubset(tables["orders"]["cols"]),
            "Price and duration must remain in orders")
    require(re.search(r"PRIMARY KEY \(order_id\)", tables["order_snapshots"]["body"]),
            "Snapshot must use the order ID as its shared primary key")
    require(re.search(r"FOREIGN KEY \(order_id\) REFERENCES orders \(id\)", tables["order_snapshots"]["body"]),
            "Snapshot must reference its order")
    require(not re.search(r"\b(current_due_at|is_overdue|revision_count|seller_balance)\b", code, re.I),
            "Unneeded derived columns found")
    if args.write_dictionary:
        path = DOCS / "physical-db.md"
        doc = path.read_text(encoding="utf-8")
        marker = "<!-- COLUMN_DICTIONARY -->"
        doc = doc.split(marker)[0].rstrip() + "\n\n" + marker + "\n\n" + "\n\n".join(dictionaries) + "\n"
        path.write_text(doc, encoding="utf-8", newline="\n")
    print(f"PASS: {len(tables)} tables, {column_count} columns, {fk_count} FKs, {check_count} CHECKs; required unique keys and FK indexes")
    print("Static consistency only: MySQL syntax, constraints and runtime behavior still require real MySQL validation.")


if __name__ == "__main__":
    main()
