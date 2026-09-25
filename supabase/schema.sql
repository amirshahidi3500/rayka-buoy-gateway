-- =====================================================================
--  RAYKA Fleet & Marine Buoy - Telemetry schema
--  این اسکریپت را یک‌بار در Supabase Dashboard > SQL Editor اجرا کنید.
--  اگر جدول telemetry از قبل با نام/ستون‌های دیگر ساخته‌اید، ابتدا آن را
--  DROP کنید یا این اسکریپت را با نام جدید (مثلاً telemetry_v2) اجرا کنید
--  و در index.html مقدار TELEMETRY_TABLE را همان نام بگذارید.
-- =====================================================================

create table if not exists public.telemetry (
    id              bigserial primary key,

    -- شناسه بویه، برابر همان چیزی که در برنامه استفاده می‌شود: مثلا RAYKA-01
    buoy_id         text not null,

    -- موقعیت و ارتفاع (از فیلدهای LAT / LON / ALT پیامک)
    lat             double precision,
    lon             double precision,
    alt             double precision,

    -- برق و انرژی (از BAT / SOLAR / CURR)
    battery_voltage numeric,      -- ولت، مثلا 12.4
    solar_voltage   numeric,      -- ولت، مثلا 14.2
    current_ma      integer,      -- میلی‌آمپر

    -- محیطی (از TEMP_EXT / HUM_INT / LEAK)
    temp_ext        numeric,      -- درجه سانتی‌گراد
    hum_int         numeric,      -- درصد رطوبت داخل محفظه
    leak            boolean,      -- true = نشتی آب تشخیص داده شده

    -- وضعیت سامانه (از SKT / OP / GPS_FIX / RSSI) - فعلا خام نگه داشته می‌شود
    skt             integer,
    op              integer,
    gps_fix         text,         -- مثلا '3D', '2D', 'NONE'
    rssi            integer,      -- dBm، قدرت آنتن GSM

    -- زمان‌بندی
    device_ts       timestamptz,  -- زمانِ خودِ دستگاه، از فیلد TS پیامک (منبع اصلی مرتب‌سازی/تاریخچه)
    created_at      timestamptz default now(),  -- زمانی که رکورد وارد سرور شد

    -- ردیابی و اشکال‌زدایی
    raw_sms         text,         -- متن کامل پیامک اصلی، برای اشکال‌زدایی/حسابرسی
    source          text default 'tasker_sms'   -- 'tasker_sms' | 'offline_local_sync' | 'manual'
);

-- جلوگیری از ثبت تکراریِ همان رکورد وقتی هم Tasker مستقیم می‌فرستد و هم بعداً
-- یک گوشی دیگر که آفلاین همان رکورد را کش کرده، آن را دوباره sync (upsert) می‌کند.
-- این همان چیزی است که کد فرانت‌اند با upsert(..., { onConflict: 'buoy_id,device_ts' }) به آن تکیه می‌کند.
create unique index if not exists telemetry_buoy_devicets_uniq
    on public.telemetry (buoy_id, device_ts);

-- برای سریع بودن کوئری «آخرین وضعیت هر بویه» و «تاریخچه ۷ روزه یک بویه»
create index if not exists telemetry_buoy_created_idx
    on public.telemetry (buoy_id, created_at desc);

create index if not exists telemetry_buoy_devicets_idx
    on public.telemetry (buoy_id, device_ts desc);

-- =====================================================================
--  Row Level Security
--  توصیه: کلید anon فقط برای SELECT (خواندن) استفاده شود. درج رکورد جدید
--  (INSERT) نباید با anon key از داخل برنامه موبایل/PWA انجام شود، چون هرکسی
--  که کلید anon را (از کد PWA) ببیند می‌تواند تله‌متری جعلی بفرستد.
--  به‌جای آن، Tasker باید یا:
--    الف) از یک Supabase Edge Function با یک کلید مخفیِ جدا (نه anon) استفاده کند، یا
--    ب) از service_role key فقط داخل یک درخواست سرور-به-سرور (نه در گوشی) استفاده شود.
--  در نبود این‌ها، حداقل یک ستون/هدر رمز مشترک (shared secret) بین Tasker و
--  یک Edge Function ساده بگذارید که قبل از insert آن را چک کند.
-- =====================================================================
alter table public.telemetry enable row level security;

-- خواندن برای همه کاربران احراز هویت‌شده (host/guest) آزاد است
create policy if not exists "telemetry_select_authenticated"
    on public.telemetry for select
    using (auth.role() = 'authenticated');

-- درج فقط از طریق service_role (Edge Function) مجاز است؛ کلید anon اجازه insert ندارد
create policy if not exists "telemetry_insert_service_role_only"
    on public.telemetry for insert
    with check (auth.role() = 'service_role');

-- =====================================================================
--  Realtime
--  در Supabase Dashboard > Database > Replication، جدول telemetry را به
--  publication مربوط به Realtime اضافه کنید (یا این دستور را اجرا کنید):
-- =====================================================================
alter publication supabase_realtime add table public.telemetry;
