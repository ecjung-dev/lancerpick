# 물리 ERD 이미지

- `lancerpick-physical-erd.png`: 4000×3320 원본, 공유·확대 확인용.
- `lancerpick-physical-erd.svg`: 벡터 원본, 확대 시 선·글자 품질 유지.
- `lancerpick-physical-erd-preview.png`: 1800×1494 미리보기.

생성 기준은 [DDL](../sql/V1__init.sql)이다. 테이블 15개·컬럼 119개·FK 22개를 추출하고 키·NULL 여부·복합 UNIQUE 그룹을 표시했다. FK 목록의 0..1/0..N은 해당 부모에 대한 자식의 개수이며, nullable FK는 점선으로 구분한다. CHECK·기본값·일반 인덱스는 SQL 원본을 참조한다. 실명·회원 데이터·접속 정보는 포함하지 않는다.

생성 도구는 [render_erd.py](../scripts/render_erd.py)이며 Python·Pillow와 Windows 맑은 고딕·Consolas 폰트를 사용한다. 저장소 루트에서 `python docs/scripts/render_erd.py`로 다시 생성한다. SQL이 변경되면 이미지를 함께 갱신한다. PNG 미리보기를 열어 표·관계선·글자 배치를 확인했다. 실제 DB 적용 검증과는 다르다.
