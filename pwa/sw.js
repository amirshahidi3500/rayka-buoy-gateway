const CACHE_NAME = 'rayka-buoy-v3';

// لیست تمام CDNها و فایل‌های داخلی که باید آفلاین ذخیره شوند
const ASSETS_TO_CACHE = [
  './',
  './index.html', // اگر اسم فایل اصلی شما dashboard (13)_2.html است، همان را بگذارید
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './icon-192-dark.png',
  './icon-512-dark.png',
  'https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.rtl.min.css',
  'https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js',
  'https://cdn.jsdelivr.net/npm/chart.js',
  'https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/dist/umd/supabase.js'
];

// ۱. رویداد Install: ذخیره فایل‌ها در Cache
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(ASSETS_TO_CACHE);
    })
  );
  self.skipWaiting();
});

// ۲. رویداد Activate: پاک‌سازی کش‌های قدیمی
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            return caches.delete(key);
          }
        })
      );
    })
  );
  self.clients.claim();
});

// ۳. رویداد Fetch: دریافت اطلاعات
self.addEventListener('fetch', (event) => {
  // ۱. عدم ذخیره‌سازی درخواست‌های مستقیم دیتابیس Supabase و WebSocket
  if (event.request.url.includes('supabase.co') || event.request.url.startsWith('wss://')) {
    return;
  }

  // ۲. عدم کش کردن درخواست‌های غیر GET (مانند POST/PUT)
  if (event.request.method !== 'GET') {
    return;
  }

  // ۳. استراتژی: اول بررسی کش (سریع‌تر و بدون خطا در آفلاین)، در صورت عدم وجود -> دریافت از شبکه
  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      if (cachedResponse) {
        // اگر فایل در کش بود، همان را برمی‌گرداند و در پس‌زمینه اپدیت می‌کند
        fetch(event.request).then((networkResponse) => {
          if (networkResponse && networkResponse.status === 200) {
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, networkResponse);
            });
          }
        }).catch(() => {/* آنلاین نیست، نادیده بگیر */});

        return cachedResponse;
      }

      // اگر در کش نبود، از شبکه دریافت کن
      return fetch(event.request).then((networkResponse) => {
        if (!networkResponse || networkResponse.status !== 200 || networkResponse.type !== 'basic') {
          return networkResponse;
        }

        const responseToCache = networkResponse.clone();
        caches.open(CACHE_NAME).then((cache) => {
          cache.put(event.request, responseToCache);
        });

        return networkResponse;
      });
    })
  );
});