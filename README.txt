FLOATGUARD DEMO FRONTEND

FILES
- index.html: complete dashboard structure
- styles.css: dashboard styling and responsive layout
- app.js: demo transactions, history, rebalance request log, local browser storage

HOW TO USE
1. Put all three files in the same folder.
2. Open index.html in your browser, or place them in your Java project's public/static folder.
3. Keep DEMO_MODE = true in app.js while demonstrating.
4. Demo starts with Cash K5,000.00 and E-Float K8,000.00.
5. Customer deposit: cash increases and e-float decreases.
6. Customer withdrawal: cash decreases and e-float increases.
7. Demo data is stored in browser localStorage. Use Reset demo data to restore starting balances.

IMPORTANT
This is a frontend-only demo. Rebalance requests are local and do not broadcast to other agents or transfer float. When backend integration begins, confirm exact endpoint paths and JSON field names with the backend developer before switching DEMO_MODE to false.
