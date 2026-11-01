# Aram Office Web Application

Spring Boot 3.3 web application for Aram Office document search, citizen-linked records, and government-officer document access.

## Database inspection and migration

The application configuration targets MariaDB database `aram_office` on port `3307` and uses `spring.jpa.hibernate.ddl-auto=none`. Back up the database before schema changes. The live schema has not been verified: the XAMPP client could not authenticate against the server found on port `3306`, and nothing was listening on the application's configured port `3307`. Do not run these migrations until the correct database endpoint and live column definitions have been confirmed.

1. Start MariaDB in XAMPP.
2. Connect to the MariaDB instance actually used by the application. From the project root, run the read-only schema preflight against its verified port:

   ```powershell
   & "C:\xampp\mysql\bin\mysql.exe" --host=127.0.0.1 --port=3307 --user=root -p aram_office --execute="source sql/officer_access_preflight.sql"
   ```

3. Confirm that the live `documents.document_id` and `departments.department_id` definitions are compatible with the migration, including type, character set, and collation. Confirm `app_users.username` is a primary/unique key. If definitions differ, adapt the migration based on the inspection before running it.
4. Confirm that the existing `document_versions` table has the columns shown in the preflight. After confirming the schema, optionally run [document version backfill](../sql/document_version_backfill.sql). It inserts only first snapshots for documents without existing versions and does not replace existing history:

   ```powershell
   & "C:\xampp\mysql\bin\mysql.exe" --host=127.0.0.1 --port=3307 --user=root -p aram_office --execute="source sql/document_version_backfill.sql"
   ```

5. After taking a backup and confirming the preflight, apply the additive [officer-access migration](../sql/officer_access_migration.sql):

   ```powershell
   & "C:\xampp\mysql\bin\mysql.exe" --host=127.0.0.1 --port=3307 --user=root -p aram_office --execute="source sql/officer_access_migration.sql"
   ```

6. If the `officers` table already exists from an earlier version, apply [optional officer department migration](../sql/officer_department_optional_migration.sql). It preserves current department values and column collation while allowing new officer records to have no department:

   ```powershell
   & "C:\xampp\mysql\bin\mysql.exe" --host=127.0.0.1 --port=3307 --user=root -p aram_office --execute="source sql/officer_department_optional_migration.sql"
   ```

7. After confirming `documents.document_id` has the same length, character set, and collation used by the new table definition, apply [document file metadata migration](../sql/document_files_migration.sql):

   ```powershell
   & "C:\xampp\mysql\bin\mysql.exe" --host=127.0.0.1 --port=3307 --user=root -p aram_office --execute="source sql/document_files_migration.sql"
   ```

   The officer migration adds departments, officer metadata, and audit events. The file migration adds only file metadata and foreign keys; it does not change existing documents or upload any files. Enabled government officers can access all documents after signing in.

8. After the file migration has completed, apply [document department metadata migration](../sql/document_departments_migration.sql). It creates the document-to-department metadata table and backfills department metadata from existing uploaded files without changing or deleting documents:

   ```powershell
   & "C:\xampp\mysql\bin\mysql.exe" --host=127.0.0.1 --port=3307 --user=root -p aram_office --execute="source sql/document_departments_migration.sql"
   ```

   This migration depends on both `departments` (from the officer migration) and `document_files` (from the file migration). Confirm that `documents.document_id` has a compatible type, character set, and collation before applying it.

The `aram_app` database user needs `SELECT`/`INSERT` on `document_files` and `document_departments`, `SELECT`/`INSERT`/`UPDATE` on `documents` and `document_versions`, and the officer/audit privileges from the existing setup. Keep existing grants and do not give the app user schema-management permissions. If an older installation already has a `document_assignments` table, the updated application ignores it and does not delete its records.

## Original PDF and image documents

Administrators can create officer accounts without department details. For existing databases, run the optional officer department migration above before using the updated officer account form. Existing officer department values are preserved. Administrators can add text documents or upload original files from **Add a document** (`http://localhost:8081/documents/add`). The form requires a department for every new document, whether it contains text only or an uploaded file. The selected department is stored as document metadata and shown on the document details page; it does not limit officer access. Each upload creates a new unique document record and never overwrites an existing `document_id`.

Accepted originals are PDF, JPG/JPEG, PNG, and WEBP, up to 10 MB. The application validates extension and file signature; it does not trust the browser MIME type. Generated UUID storage names are kept in `document_files`; uploaded bytes are stored outside static/public resources in `${ARAM_DOCUMENT_STORAGE_DIR}` or, by default, `${user.home}/.aram-office/documents`. On POSIX systems the application restricts the storage directory and files to the application user. On Windows, grant access to the configured NTFS storage folder only to the application service account and administrators. Back up this private folder alongside the database; a database backup alone does not include original uploaded files.

Document details show the original name, type, size, uploader, upload date, department, and preview/download links. PDF and image preview/download URLs are authenticated routes that re-check that the requester is an enabled government officer (or an administrator) on every request. Response headers prevent MIME sniffing and caching. The app does not expose the private filesystem path.

## Create the initial administrator

Public registration is disabled. Create the first administrator through environment configuration before starting the application, or use an existing administrator account. The application stores only a BCrypt password hash:

```powershell
$env:ARAM_ADMIN_USERNAME = "office-admin"
$env:ARAM_ADMIN_PASSWORD = "replace-with-a-12-to-72-byte-password"
Set-Location .\webapp
& "C:\tools\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run
```

Open `http://localhost:8081/login`. After signing in as an administrator, use **Administration** at `/admin` to:

- create officer accounts without department details;
- add searchable documents or upload original PDF/image files from the same form;
- inspect recent login/access/account audit events.
- review saved text revisions on authorized document details; administrators can save new revisions without deleting earlier text.

Officer accounts use distinct usernames and BCrypt hashes. All enabled officers can search, view, preview, and download documents in the repository. Disabled accounts cannot sign in or access documents.

## Isolated demo profile

For a disposable local demo without MariaDB, package the app and start its H2 profile. Set temporary administrator credentials in the same PowerShell window; do not commit them:

```powershell
$env:ARAM_ADMIN_USERNAME = "office-admin"
$env:ARAM_ADMIN_PASSWORD = "replace-with-a-12-to-72-byte-password"
& "C:\tools\apache-maven-3.9.9\bin\mvn.cmd" -f "webapp\pom.xml" "-Dmaven.test.skip=true" package
java -jar "webapp\target\egovernance-demo-web-1.0.0.jar" --spring.profiles.active=demo --server.port=8081
```

The demo profile provides synthetic Revenue, Health, and Education departments and documents. It uses an in-memory H2 database and discards demo accounts and audit records when stopped. Original demo files are stored in the separate private demo storage directory; remove that directory when clearing presentation data. The demo does not connect to or alter MariaDB.

## Existing citizen access

Existing citizen search remains restricted by `citizen_user_access`. Citizen-linked records and government-officer access use separate authorization checks. See the existing [person-search migration](../sql/person_search_migration.sql) and reviewed example links in [link_existing_documents.example.sql](../sql/link_existing_documents.example.sql).

## Tests

Run the full H2-backed test suite from PowerShell:

```powershell
Set-Location .\webapp
& "C:\tools\apache-maven-3.9.9\bin\mvn.cmd" test
```

The tests cover existing citizen/search behavior, shared document access for enabled officers, officer creation without a department, disabled-officer access denial, administrator-only account/document controls, PDF/PNG upload from Add New Document, rejection of unsupported or mismatched signatures and oversized files, saved version history, and protected preview/download routes.
