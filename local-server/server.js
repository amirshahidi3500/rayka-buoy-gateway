// یک سرور محلی ساده و بدون هیچ وابستگی خارجی (فقط Node.js لازم است).
// این را می‌توانید روی یک گوشی قدیمی (با Termux)، رزبری‌پای، یا لپ‌تاپی که
// هات‌اسپات/مودم همان شبکه‌ی محلی است اجرا کنید.
//
// اجرا:   node server.js
// پیش‌فرض روی پورت ۸۷۸۷ بالا می‌آید: http://<آی‌پی این دستگاه در شبکه محلی>:8787
//
// اپ RAYKA Buoy Gateway وقتی اینترنت گوشی قطع باشد، پیام‌های خام را با POST به
// آدرس /ingest همین سرور می‌فرستد؛ و PWA می‌تواند با GET /telemetry آخرین
// پیام‌ها را از همین سرور محلی (بدون نیاز به اینترنت) بخواند.

const http = require('http');
const fs = require('fs');

const PORT = process.env.PORT || 8787;
const SHARED_SECRET = process.env.LOCAL_SECRET || ''; // خالی = بدون احراز هویت (فقط برای شبکه‌ی کاملاً مورد اعتماد)
const STORAGE_FILE = './telemetry_local_store.json';

let store = [];
try {
  store = JSON.parse(fs.readFileSync(STORAGE_FILE, 'utf8'));
} catch (e) {
  store = [];
}

function persist() {
  fs.writeFile(STORAGE_FILE, JSON.stringify(store.slice(-2000)), () => {});
}

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');

  if (req.method === 'POST' && req.url === '/ingest') {
    if (SHARED_SECRET && req.headers['x-local-secret'] !== SHARED_SECRET) {
      res.writeHead(401);
      return res.end('Unauthorized');
    }
    let body = '';
    req.on('data', (chunk) => (body += chunk));
    req.on('end', () => {
      try {
        const data = JSON.parse(body);
        store.push({ ...data, received_local_at: Date.now() });
        persist();
        res.writeHead(200);
        res.end('OK');
      } catch (e) {
        res.writeHead(400);
        res.end('Invalid JSON');
      }
    });
    return;
  }

  if (req.method === 'GET' && req.url.startsWith('/telemetry')) {
    res.setHeader('Content-Type', 'application/json');
    res.writeHead(200);
    res.end(JSON.stringify(store.slice(-200)));
    return;
  }

  if (req.method === 'GET' && req.url.startsWith('/health')) {
    res.writeHead(200);
    return res.end('OK');
  }

  res.writeHead(404);
  res.end('Not found');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Local RAYKA telemetry server running on http://0.0.0.0:${PORT}`);
});
