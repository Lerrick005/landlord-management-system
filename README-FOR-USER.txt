===============================================================================
  TENANT & PROPERTY MANAGEMENT SYSTEM — INSTALLATION GUIDE
===============================================================================

Thank you for purchasing the Tenant & Property Management System.

Follow these steps ONCE to set up the software on your computer.


------------------------------------------------------------------------------
  STEP 1 — INSTALL POSTGRESQL (one-time setup)
------------------------------------------------------------------------------

The system stores all your data in a free database called PostgreSQL.
You need to install it once before using the software.

  1. Visit: https://www.postgresql.org/download/windows/

  2. Download the Windows installer (version 16 or newer).

  3. Run the installer. Follow the default options.

  4. When it asks for a password for the "postgres" user:
        ** CHOOSE A PASSWORD AND WRITE IT DOWN **
     You will need this password later.

  5. Finish the installation. PostgreSQL will start automatically.


------------------------------------------------------------------------------
  STEP 2 — CREATE THE DATABASE
------------------------------------------------------------------------------

  1. Open the "SQL Shell (psql)" program that was installed with PostgreSQL.
     (Look for it in the Start Menu.)

  2. Press Enter to accept the default values for Server, Database, Port, and
     Username. When it asks for the password, type the password you chose
     in Step 1.

  3. At the "postgres=#" prompt, type exactly:

        CREATE DATABASE property_management;

     and press Enter. You should see: CREATE DATABASE

  4. Type "\q" and press Enter to exit.


------------------------------------------------------------------------------
  STEP 3 — INSTALL THE APPLICATION
------------------------------------------------------------------------------

  1. Double-click "TenantManagementSystem-1.0.0.exe"

  2. Follow the installer prompts.

  3. Choose where to install (the default is fine).

  4. The installer will create:
        - A Start Menu shortcut
        - A Desktop shortcut
        - An entry in "Add or Remove Programs"

  5. Click "Finish" when done.


------------------------------------------------------------------------------
  STEP 4 — FIRST LAUNCH
------------------------------------------------------------------------------

  1. Double-click the desktop icon or open it from the Start Menu.

  2. On the very first launch, a setup wizard will appear.

  3. Enter your name, choose a username and password, and enter your
     organization's details.

  4. Click "Complete Setup".

  5. You will then see the login window. Log in with the account you just
     created.


------------------------------------------------------------------------------
  EVERYDAY USE
------------------------------------------------------------------------------

Once set up, the software opens directly to the login screen. No additional
steps are needed.


------------------------------------------------------------------------------
  BACKING UP YOUR DATA
------------------------------------------------------------------------------

The system includes a built-in backup feature.

  1. Log in as an administrator.

  2. Click "Settings" in the sidebar.

  3. Under the "Backup" section, click "Browse" and choose a folder where
     backups should be saved. (A USB drive or cloud-synced folder is ideal.)

  4. Click "Save Settings".

  5. To backup, click "Backup Now". A timestamped file will be saved to
     your chosen folder.

  6. To restore from a backup, click "Restore from Backup..." and pick a
     previously saved file.

  *** IMPORTANT ***
  Make a backup at least once a week. Backups protect your business records
  if your computer is lost or damaged.


------------------------------------------------------------------------------
  TROUBLESHOOTING
------------------------------------------------------------------------------

  Problem: "Cannot reach the database."

    - Check that PostgreSQL is running. Look for "postgresql" in Windows
      Services (press Windows key, type "services", hit Enter).

    - Verify the PostgreSQL password in your app settings matches the one
      you chose during PostgreSQL installation.

    - Check that the database "property_management" exists (repeat Step 2).

  Problem: The app won't start.

    - Try restarting your computer.

    - Reinstall the application.

    - Contact your software provider with a screenshot of the error.

  Problem: I forgot my password.

    - Passwords cannot be recovered from outside the system.
      Log in as another administrator and use the "Reset Password" option
      from the Users screen.

    - If you are the only administrator, contact your software provider.


------------------------------------------------------------------------------
  SYSTEM REQUIREMENTS
------------------------------------------------------------------------------

  - Windows 10 or Windows 11 (64-bit)

  - 4 GB RAM minimum

  - 500 MB free disk space for the application

  - An additional 200 MB for PostgreSQL

  - A modern printer (optional — for printing receipts and reports)


------------------------------------------------------------------------------
  SUPPORT
------------------------------------------------------------------------------

  Email: [your support email]
  Phone: [your support phone]

  Please keep this file for future reference.
===============================================================================
