-- Smart Sales Pro · Supabase 스키마
-- Supabase 대시보드 > SQL Editor 에 붙여넣고 실행

-- 1. 거래처
create table hospitals (
  id bigint generated always as identity primary key,
  name text not null,
  chart jsonb default '[]',          -- 제품 비교 차트 [{metric, ours, theirs}]
  created_at timestamptz default now()
);

-- 2. 담당 의사/공무원 (officials)
create table officials (
  id bigint generated always as identity primary key,
  hospital_id bigint references hospitals(id) on delete cascade,
  doctor_name text,
  department text,
  keywords text[] default '{}',      -- 과거 상담 핵심 키워드
  created_at timestamptz default now()
);

-- 3. 일보 (daily_logs)
create table daily_logs (
  id bigint generated always as identity primary key,
  hospital_id bigint references hospitals(id) on delete cascade,
  note text,
  created_at timestamptz default now()
);

-- 4. 경비 (expenses) — localStorage 대체, 기기 교체에도 유지
create table expenses (
  id bigint generated always as identity primary key,
  hospital_id bigint references hospitals(id) on delete set null,
  category text not null,
  memo text,
  amount integer not null check (amount > 0),
  expense_date date not null,
  created_at timestamptz default now()
);

-- RLS (개인 사용 시 anon 읽기 허용 예시 — 운영 시 auth 기반으로 강화 권장)
alter table hospitals enable row level security;
alter table officials enable row level security;
alter table daily_logs enable row level security;
alter table expenses enable row level security;
create policy "anon read hospitals" on hospitals for select using (true);
create policy "anon read officials" on officials for select using (true);
create policy "anon read logs" on daily_logs for select using (true);
-- expenses 는 앱에서 anon key로 직접 추가/삭제하므로 CRUD 전체 허용
create policy "anon read expenses" on expenses for select using (true);
create policy "anon insert expenses" on expenses for insert with check (true);
create policy "anon delete expenses" on expenses for delete using (true);

-- 샘플 데이터
insert into hospitals (name, chart) values
('서울중앙병원', '[{"metric":"약가 대비 효율","ours":92,"theirs":70},{"metric":"부작용 발생률","ours":88,"theirs":62},{"metric":"복용 편의성","ours":80,"theirs":75}]');
-- 한 병원에 담당의 다수 등록 가능 (officials.hospital_id 로 1:N 관계)
insert into officials (hospital_id, doctor_name, department, keywords) values
(1, '김철수 과장', '내과', array['가격 경쟁력','부작용 우려','월 처방량 확대']),
(1, '정미라 과장', '외과', array['수술 후 통증 관리','재고 확보','임상 사례 자료']);
insert into daily_logs (hospital_id, note) values
(1, '지난번 경쟁사 A제품 언급, 가격 민감');
