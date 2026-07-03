# Smart Sales Pro — Build Spec (Claude Code용)

## 목표
정적 PWA. 빌드 스텝 없이 파일 3개로 동작. Supabase 키 있으면 Live, 없으면 Mock 폴백.

## 파일 구조 (그대로 배치)
```
smart-sales-pro/
├── index.html          # 앱 전체 (UI + 로직)
├── manifest.json       # PWA 매니페스트
├── service-worker.js   # 오프라인 캐싱
├── supabase_schema.sql # DB 테이블 생성 SQL
├── vendor/
│   └── supabase.js     # Supabase JS UMD 번들 (로컬, CDN 의존 없음)
└── README.md           # 이 파일
```

## CDN 의존 제거됨
- Supabase JS는 `vendor/supabase.js` 로컬 번들 사용 → 사내망/오프라인/CDN 차단 환경에서도 로드
- 폰트는 Pretendard 우선 + 시스템 한글폰트(Apple SD Gothic Neo/맑은 고딕/Noto Sans KR) 폴백 → Pretendard CDN이 막혀도 한글이 깨지지 않음

## 로컬 실행 (SW·localStorage 검증하려면 반드시 http 서버로)
```bash
cd smart-sales-pro
python3 -m http.server 5173
# → http://localhost:5173  (file:// 로 열면 SW/PWA 안 됨)
```

## Supabase 연동 순서
1. supabase.com 프로젝트 생성
2. SQL Editor → `supabase_schema.sql` 전체 붙여넣고 실행
3. Project Settings → API 에서 `Project URL`, `anon public key` 복사
4. `index.html` 상단 `SUPABASE_URL`, `SUPABASE_KEY` 두 상수에 붙여넣기
5. 새로고침 → 상단 배너가 "Live"로 바뀌면 연동 성공

## 배포
- Vercel/Netlify: 루트를 `smart-sales-pro/`로 지정, 빌드 커맨드 없음, 정적 배포
- HTTPS 필수 (SW/PWA 조건)

## 데이터 모델
| 테이블 | 용도 | 핵심 필드 |
|---|---|---|
| hospitals | 거래처 | name, chart(jsonb) |
| officials | 담당의 | hospital_id, doctor_name, department, keywords(text[]) |
| daily_logs | 일보 | hospital_id, note, created_at |
| expenses | 경비 | hospital_id(nullable), category, memo, amount, expense_date |

## 알려진 제약 (Claude Code가 손댈 후보)
1. ~~officials 병원당 1명만 조인(`officials[0]`) — 다수 담당의 유실~~ → 해결: 모달 내 의사 탭으로 다수 담당의 지원
2. ~~경비는 localStorage — 기기 교체 시 증발, DB 미승격~~ → 해결: Supabase `expenses` 테이블 연동 (Live 시 DB 저장, Mock/미연동 시 localStorage 폴백)
3. 비교 차트 수치는 하드코딩 주관값 — 근거 링크 없음
4. RLS anon read 전체 허용 — 운영 시 auth 기반으로 강화 필요
