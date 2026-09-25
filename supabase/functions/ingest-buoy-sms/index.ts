// supabase/functions/ingest-buoy-sms/index.ts
//
// این تابع را با دستورهای زیر روی Supabase منتشر کنید:
//   supabase functions new ingest-buoy-sms   (سپس این فایل را جایگزین محتوای تولیدشده کنید)
//   supabase secrets set TASKER_SHARED_SECRET=<همان رشته رمزی که در هدر Tasker گذاشتید>
//   supabase functions deploy ingest-buoy-sms
//
// Tasker با POST و هدر X-Tasker-Secret و بدنه {"sms": "..."} به این آدرس می‌زند.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SHARED_SECRET = Deno.env.get("TASKER_SHARED_SECRET")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

// همان منطق parseRaykaSms که در index.html هم هست
function parseRaykaSms(raw: string) {
  const body = raw.trim().replace(/^RA[IY]KA:/i, "");
  const kv: Record<string, string> = {};
  body.split(",").forEach((part) => {
    const i = part.indexOf("=");
    if (i === -1) return;
    kv[part.slice(0, i).trim().toUpperCase()] = part.slice(i + 1).trim();
  });

  const num = (s?: string) => (s === undefined || s === "" ? null : parseFloat(s.replace(/[^\d.\-]/g, "")));
  const boolFlag = (s?: string) => (s === undefined || s === "" ? null : s === "1");

  let deviceTs: string | null = null;
  if (kv.TS && /^\d{10}$/.test(kv.TS)) {
    const yy = 2000 + parseInt(kv.TS.slice(0, 2), 10);
    const mo = parseInt(kv.TS.slice(2, 4), 10) - 1;
    const dd = parseInt(kv.TS.slice(4, 6), 10);
    const hh = parseInt(kv.TS.slice(6, 8), 10);
    const mi = parseInt(kv.TS.slice(8, 10), 10);
    deviceTs = new Date(Date.UTC(yy, mo, dd, hh, mi)).toISOString();
  }

  if (!kv.ID) return null;

  return {
    buoy_id: `RAYKA-${kv.ID.padStart(2, "0")}`,
    lat: num(kv.LAT),
    lon: num(kv.LON),
    alt: num(kv.ALT),
    battery_voltage: num(kv.BAT),
    solar_voltage: num(kv.SOLAR),
    current_ma: num(kv.CURR),
    temp_ext: num(kv.TEMP_EXT),
    hum_int: num(kv.HUM_INT),
    leak: boolFlag(kv.LEAK),
    skt: kv.SKT !== undefined ? parseInt(kv.SKT, 10) : null,
    op: kv.OP !== undefined ? parseInt(kv.OP, 10) : null,
    gps_fix: kv.GPS_FIX ?? null,
    rssi: num(kv.RSSI),
    device_ts: deviceTs,
    raw_sms: raw,
    source: "tasker_sms",
  };
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }
  if (req.headers.get("X-Tasker-Secret") !== SHARED_SECRET) {
    return new Response("Unauthorized", { status: 401 });
  }

  let payload: { sms?: string };
  try {
    payload = await req.json();
  } catch {
    return new Response("Invalid JSON body", { status: 400 });
  }

  const row = parseRaykaSms(payload.sms ?? "");
  if (!row) {
    return new Response("Could not parse SMS (missing ID field?)", { status: 400 });
  }

  const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const { error } = await supabase
    .from("telemetry")
    .upsert(row, { onConflict: "buoy_id,device_ts" });

  if (error) {
    return new Response(`DB error: ${error.message}`, { status: 500 });
  }

  return new Response("OK", { status: 200 });
});
