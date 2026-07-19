# API Collections Index

This index explains the three artifacts included in this repository for testing and documenting the REST and SOAP APIs.

## Files 

| File | Type | Purpose | How to use |
|---|---|---|---|
| `docs/postman-rest-collection.json` | Postman Collection (REST) | Full set of REST endpoints (GET/POST/PUT/DELETE). Includes example bodies and headers. | Import into Postman (or compatible tools) to run requests against your environments. |
| `docs/postman-soap-collection.json` | Postman Collection (SOAP) | SOAP requests packaged for Postman (HTTP + XML payloads). | Import into Postman; edit SOAP XML bodies and auth as needed. |
| `docs/api-request-response-matrix.xlsx` | Excel Workbook | Canonical table of endpoints with method, path/action, expected request body, response body, and status codes. | Open to review coverage, expected fields (incl. null/empty arrays), and error handling. Useful for QA and third-party integrators. |
