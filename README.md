# 🏢 Landlord Management System

> A desktop application designed to help landlords efficiently manage tenants, allocate rooms, and streamline property administration.

![Java](https://img.shields.io/badge/Java-95.7%25-ED8B00?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?logo=apache-maven&logoColor=white)
![CSS](https://img.shields.io/badge/CSS-UI_Styling-1572B6?logo=css3&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-Windows-0078D6?logo=windows&logoColor=white)

## 📖 Overview

The **Landlord Management System** is a Java-based desktop application built to simplify property management for landlords. It replaces manual tracking (like spreadsheets or paper records) with a centralized digital system to manage room allocations and tenant lifecycles.

## ✨ Key Features

*   **Tenant Management:** Easily add, view, and manage tenant details.
*   **Room Allocation:** Assign new rooms to incoming tenants and track occupancy.
*   **Tenant Removal:** Quickly remove previous tenants from the system to free up room availability.
*   **Desktop UI:** A styled graphical user interface (CSS-driven) for an intuitive desktop experience.

## 🛠️ Tech Stack

*   **Language:** Java
*   **Build Tool:** Apache Maven (`pom.xml`)
*   **UI Styling:** CSS
*   **Deployment:** Custom PowerShell installer script (`build-installer.ps1`)

## 📂 Project Structure

```text
landlord-management-system/
├── src/main/                # Application source code (Java & CSS)
├── docs/                    # Documentation
├── .vscode/                 # VS Code workspace settings
├── app.ico                  # Application icon for the installer
├── build-installer.ps1      # PowerShell script to build the Windows installer
├── pom.xml                  # Maven configuration and dependencies
├── README-FOR-USER.txt      # End-user instructions
└── README.md
