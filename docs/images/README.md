# 물리 ERD 이미지

- `lancerpick-physical-erd.png`: 4000×3320 원본, 공유·확대 확인용.
- `lancerpick-physical-erd.svg`: 벡터 원본, 확대 시 선·글자 품질 유지.
- `lancerpick-physical-erd-preview.png`: 1800×1494 미리보기.

생성 기준은 [DDL](../sql/V1__init.sql)이다. 테이블 16개·컬럼 120개·FK 23개를 추출하고 키·NULL 여부·복합 UNIQUE 그룹을 표시했다. FK 목록의 0..1/0..N은 해당 부모에 대한 자식의 개수이며, nullable FK는 점선으로 구분한다. CHECK·기본값·일반 인덱스는 SQL 원본을 참조한다. 각 테이블에 확정된 주담당자 이름(김창재·박소현·정은찬·황준범)을 표시한다. 실제 회원 데이터·접속 정보는 포함하지 않는다.

생성 도구는 [render_erd.py](../scripts/render_erd.py)이며 Python·Pillow와 Windows 맑은 고딕·Consolas 폰트를 사용한다. 저장소 루트에서 `python docs/scripts/render_erd.py`로 다시 생성한다. SQL이 변경되면 이미지를 함께 갱신한다. PNG 미리보기를 열어 표·관계선·글자 배치를 확인했다. 실제 DB 적용 검증과는 다르다.

주문 조건 분리 반영: orders에는 가격·소요일·진행 정보를 남기고 order_snapshots에 상품 버전·상품명·설명·제공 범위·준비사항·거래 규칙·요구사항을 표시했다. DB는 주문당 스냅샷 최대 1건을 보장하므로 물리 FK 목록은 0..1이며, 모든 주문의 스냅샷 필수 생성은 서비스가 보장한다. 주문 상태·시각·TEXT 길이 CHECK의 보완은 SQL에 있으며 관계도에는 CHECK 본문을 생략한다.
