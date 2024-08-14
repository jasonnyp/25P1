## ENHANCe
ENHANCe is an application designed to improve manual blood pressure logging with an app based on OCR technology, replacing traditional paper logged patient blood pressure with a online database storage system and a modern and simplistic mobile app.

## Features
### Login System and Staff/Admin System
To facilitate clinic staff, ENHANce includes a login page synced with an online database of users.
### Patient Searching 
In order to organise and help clinics manage the large amount of patients, a simple patient search with a simple textbox searches the database based on their ID.
### Patient Profiles and Visits
Each individual patient have records specific to them, along with every visit and past scan.
### Recommendations and Blood Pressure
The app stores each visit, and uses an algorithm to determine the BP stage of the patient.
### OCR Technology
Using industry-standard OCR scanning technology, the app allows clinics to scan paper records and implements them into the app to swiftly record each relevant record with high accuracy.
### Patient History
All patient scan history is stored into the database, with Average Home BP, Clinic BP, and simple recommendations for the patient to improve their blood pressure.
### Dashboard
Every patient has a dashboard native to their individual records, allowing doctors and clinics to see the progress of the patient, and print out physical graphs for their own reference.
### Localisation 
In order to improve the readability and accessibility of the app, the app offers different languages other than English.
### Themes
The app offers a dark and light theme to keep in line with modern UI standards.

## Implementation 
Add the project via your Android studio, and wait for the gradle build before running the project. 

**IMPORTANT: AVOID UPDATING THE GRADLE FILE IMPLEMENTATIONS, AS IT MAY BREAK THE WHOLE PROJECT. MAKE A SEPARATE BRANCH TO TEST BEFORE MAKING ANY MAJOR CHANGES.**
(Adding your own implementations won't affect the code.)

## Technical
### Data Structure
The main file structure contains 2 groups of files: Kotlin/Java files, and XML files. 
#### XML 
- UI pages (under res/layout)
- Vector icon files (under res/drawable)
- Dynamic languages and themes (under res/values)
XML is vital to the frontend of the webpage, and where much of the dynamic nature of the project has been developed from, particularly affecting languages and themes.
#### Kotlin/Java 
- Backend functionality
- Activity files (under com.singhealth.enhance/activities)
- Security and language functionality (under com.singhealth.enhance/security and com.singhealth.enhance)
Primarily where most of the app is done through (More specifics are below). The project is fully coded in Kotlin, but some parts involve imports from Java libraries. Activity files are some of the most important files, defining backend and even some frontend elements in their corresponding XML files (ie. ThemeActivity.kt -> activity_theme.xml). Most functional development will be done through these files, and are defined through the AndroidManifest.xml file.
#### AndroidManifest.xml
- Defining Activity files and permissions the app needs.
ENHANCe currently requires camera and photo access for the OCR, which is definied in this file. More importantly, Activity files are created through here, and can only be used if defined here. For connecting XML files to the Activity files, copy over some codes from an existing activity file, define the Activity in the Manifest, and ensure that the class file contains ```: AppCompatActivity()``` at the end. Also make sure to check imports and refer to other Activity files.

``` kotlin
class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
```
