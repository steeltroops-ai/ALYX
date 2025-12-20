# CDN Integration and Static Asset Optimization for ALYX Frontend

## Overview
This document outlines the CDN integration strategy and static asset optimization for the ALYX frontend to improve loading performance and reduce server load.

## CDN Configuration

### CloudFront Distribution Setup
```yaml
# cloudfront-distribution.yml
AWSTemplateFormatVersion: '2010-09-09'
Description: 'CloudFront distribution for ALYX frontend assets'

Resources:
  AlyxCDNDistribution:
    Type: AWS::CloudFront::Distribution
    Properties:
      DistributionConfig:
        Enabled: true
        Comment: 'ALYX Frontend CDN Distribution'
        DefaultRootObject: 'index.html'
        
        Origins:
          - Id: AlyxS3Origin
            DomainName: !GetAtt AlyxAssetsBucket.DomainName
            S3OriginConfig:
              OriginAccessIdentity: !Sub 'origin-access-identity/cloudfront/${OriginAccessIdentity}'
        
        DefaultCacheBehavior:
          TargetOriginId: AlyxS3Origin
          ViewerProtocolPolicy: redirect-to-https
          CachePolicyId: 4135ea2d-6df8-44a3-9df3-4b5a84be39ad  # Managed-CachingOptimized
          OriginRequestPolicyId: 88a5eaf4-2fd4-4709-b370-b4c650ea3fcf  # Managed-CORS-S3Origin
          
        CacheBehaviors:
          # Static assets with long cache
          - PathPattern: '/assets/*'
            TargetOriginId: AlyxS3Origin
            ViewerProtocolPolicy: redirect-to-https
            CachePolicyId: 4135ea2d-6df8-44a3-9df3-4b5a84be39ad
            TTL: 31536000  # 1 year
            
          # API calls - no caching
          - PathPattern: '/api/*'
            TargetOriginId: AlyxAPIOrigin
            ViewerProtocolPolicy: redirect-to-https
            CachePolicyId: 4135ea2d-6df8-44a3-9df3-4b5a84be39ad
            TTL: 0
            
        PriceClass: PriceClass_100  # Use only North America and Europe edge locations
        
        ViewerCertificate:
          AcmCertificateArn: !Ref SSLCertificate
          SslSupportMethod: sni-only
          MinimumProtocolVersion: TLSv1.2_2021
```

### Asset Optimization Strategy

#### Bundle Splitting Configuration
```typescript
// vite.config.ts - Enhanced build configuration
export default defineConfig({
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Core React libraries
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          
          // UI Framework
          'mui-vendor': ['@mui/material', '@mui/icons-material', '@emotion/react'],
          
          // Visualization libraries
          'viz-vendor': ['three', 'd3'],
          
          // Analysis tools
          'analysis-vendor': ['monaco-editor'],
          
          // State management
          'state-vendor': ['@reduxjs/toolkit', 'react-redux'],
          
          // Utilities
          'utils-vendor': ['uuid', 'socket.io-client'],
        },
        
        // Optimize chunk naming for better caching
        chunkFileNames: (chunkInfo) => {
          const facadeModuleId = chunkInfo.facadeModuleId 
            ? chunkInfo.facadeModuleId.split('/').pop().replace('.tsx', '').replace('.ts', '')
            : 'chunk';
          return `assets/js/[name]-[hash].js`;
        },
        
        assetFileNames: (assetInfo) => {
          const info = assetInfo.name.split('.');
          const ext = info[info.length - 1];
          
          if (/\.(png|jpe?g|svg|gif|tiff|bmp|ico)$/i.test(assetInfo.name)) {
            return `assets/images/[name]-[hash][extname]`;
          }
          if (/\.(woff2?|eot|ttf|otf)$/i.test(assetInfo.name)) {
            return `assets/fonts/[name]-[hash][extname]`;
          }
          return `assets/[ext]/[name]-[hash][extname]`;
        },
      },
    },
    
    // Optimize for production
    target: 'es2020',
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
        pure_funcs: ['console.log', 'console.info'],
      },
      mangle: {
        safari10: true,
      },
    },
    
    // Enable gzip compression
    reportCompressedSize: true,
    chunkSizeWarningLimit: 1000,
  },
});
```

#### Image Optimization
```typescript
// Image optimization plugin configuration
import { defineConfig } from 'vite';
import { ViteImageOptimize } from 'vite-plugin-imagemin';

export default defineConfig({
  plugins: [
    ViteImageOptimize({
      gifsicle: { optimizationLevel: 7 },
      mozjpeg: { quality: 85 },
      optipng: { optimizationLevel: 7 },
      pngquant: { quality: [0.65, 0.8] },
      svgo: {
        plugins: [
          { name: 'removeViewBox', active: false },
          { name: 'removeEmptyAttrs', active: false },
        ],
      },
    }),
  ],
});
```

## Performance Monitoring

### Web Vitals Tracking
```typescript
// src/utils/performance.ts
import { getCLS, getFID, getFCP, getLCP, getTTFB } from 'web-vitals';

interface PerformanceMetric {
  name: string;
  value: number;
  rating: 'good' | 'needs-improvement' | 'poor';
  timestamp: number;
}

class PerformanceMonitor {
  private metrics: PerformanceMetric[] = [];
  
  constructor() {
    this.initializeWebVitals();
  }
  
  private initializeWebVitals() {
    getCLS(this.handleMetric.bind(this));
    getFID(this.handleMetric.bind(this));
    getFCP(this.handleMetric.bind(this));
    getLCP(this.handleMetric.bind(this));
    getTTFB(this.handleMetric.bind(this));
  }
  
  private handleMetric(metric: any) {
    const performanceMetric: PerformanceMetric = {
      name: metric.name,
      value: metric.value,
      rating: metric.rating,
      timestamp: Date.now(),
    };
    
    this.metrics.push(performanceMetric);
    
    // Send to analytics
    this.sendToAnalytics(performanceMetric);
    
    // Log performance issues
    if (metric.rating === 'poor') {
      console.warn(`Poor ${metric.name} performance:`, metric.value);
    }
  }
  
  private sendToAnalytics(metric: PerformanceMetric) {
    // Send to your analytics service
    fetch('/api/analytics/performance', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(metric),
    }).catch(console.error);
  }
  
  public getMetrics(): PerformanceMetric[] {
    return [...this.metrics];
  }
  
  public getMetricsByName(name: string): PerformanceMetric[] {
    return this.metrics.filter(m => m.name === name);
  }
}

export const performanceMonitor = new PerformanceMonitor();
```

### Bundle Analysis
```json
{
  "scripts": {
    "analyze": "npm run build && npx vite-bundle-analyzer dist",
    "build:analyze": "vite build --mode analyze",
    "lighthouse": "lighthouse http://localhost:3000 --output html --output-path ./lighthouse-report.html"
  }
}
```

## Caching Strategy

### Service Worker Configuration
```typescript
// public/sw.js - Service Worker for aggressive caching
const CACHE_NAME = 'alyx-v1.0.0';
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/assets/js/react-vendor.js',
  '/assets/js/mui-vendor.js',
  '/assets/js/viz-vendor.js',
  '/assets/css/main.css',
];

const API_CACHE_NAME = 'alyx-api-v1.0.0';
const CACHEABLE_APIS = [
  '/api/data/events/search',
  '/api/visualization/events',
  '/api/jobs/status',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(STATIC_ASSETS))
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);
  
  // Cache static assets
  if (STATIC_ASSETS.some(asset => url.pathname.includes(asset))) {
    event.respondWith(
      caches.match(request)
        .then(response => response || fetch(request))
    );
    return;
  }
  
  // Cache API responses with TTL
  if (CACHEABLE_APIS.some(api => url.pathname.startsWith(api))) {
    event.respondWith(
      caches.open(API_CACHE_NAME)
        .then(cache => {
          return cache.match(request)
            .then(response => {
              if (response) {
                const cachedTime = response.headers.get('cached-time');
                const now = Date.now();
                const fiveMinutes = 5 * 60 * 1000;
                
                if (cachedTime && (now - parseInt(cachedTime)) < fiveMinutes) {
                  return response;
                }
              }
              
              return fetch(request)
                .then(fetchResponse => {
                  const responseClone = fetchResponse.clone();
                  const headers = new Headers(responseClone.headers);
                  headers.set('cached-time', Date.now().toString());
                  
                  const modifiedResponse = new Response(responseClone.body, {
                    status: responseClone.status,
                    statusText: responseClone.statusText,
                    headers: headers,
                  });
                  
                  cache.put(request, modifiedResponse.clone());
                  return fetchResponse;
                });
            });
        })
    );
  }
});
```

### HTTP Caching Headers
```nginx
# nginx.conf - Optimized caching configuration
server {
    listen 80;
    server_name alyx.physics.org;
    
    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types
        text/plain
        text/css
        text/xml
        text/javascript
        application/javascript
        application/xml+rss
        application/json;
    
    # Static assets with long cache
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        add_header X-Content-Type-Options nosniff;
    }
    
    # HTML files with short cache
    location ~* \.html$ {
        expires 1h;
        add_header Cache-Control "public, must-revalidate";
    }
    
    # API responses - no cache
    location /api/ {
        expires -1;
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        proxy_pass http://backend;
    }
    
    # Enable HTTP/2 Server Push for critical resources
    location = /index.html {
        http2_push /assets/js/react-vendor.js;
        http2_push /assets/js/mui-vendor.js;
        http2_push /assets/css/main.css;
    }
}
```

## Performance Optimization Checklist

### Build Optimization
- [x] Bundle splitting for vendor libraries
- [x] Tree shaking for unused code elimination
- [x] Minification and compression
- [x] Image optimization and WebP conversion
- [x] Font subsetting and preloading
- [x] CSS purging for unused styles

### Runtime Optimization
- [x] Lazy loading for route components
- [x] Virtual scrolling for large datasets
- [x] Memoization for expensive calculations
- [x] Debouncing for user input handlers
- [x] Web Workers for heavy computations
- [x] Service Worker for offline functionality

### Network Optimization
- [x] CDN integration for global distribution
- [x] HTTP/2 Server Push for critical resources
- [x] Resource hints (preload, prefetch, preconnect)
- [x] Compression (Gzip/Brotli)
- [x] Caching strategy implementation
- [x] API response optimization

### Monitoring and Analytics
- [x] Web Vitals tracking
- [x] Bundle size monitoring
- [x] Performance budget enforcement
- [x] Real User Monitoring (RUM)
- [x] Lighthouse CI integration
- [x] Error tracking and reporting

## Performance Targets

### Loading Performance
- First Contentful Paint (FCP): < 1.5s
- Largest Contentful Paint (LCP): < 2.5s
- Time to Interactive (TTI): < 3.5s
- First Input Delay (FID): < 100ms
- Cumulative Layout Shift (CLS): < 0.1

### Bundle Size Targets
- Initial bundle: < 200KB (gzipped)
- Vendor chunks: < 500KB (gzipped)
- Route chunks: < 100KB (gzipped)
- Total bundle: < 1MB (gzipped)

### Runtime Performance
- 60 FPS for animations and interactions
- < 100ms response time for user interactions
- < 16ms for frame rendering
- Memory usage < 100MB for typical sessions