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
#### Gradle (build.gradle.kts (:app))
- Defines all of the implementations (imports) that make the app work.
- Defines some of the advanced android options.
- Highly volatile, avoid changing existing options if possible.

### LoginActivity.kt -> activity_login.xml
- Handles the login of the user based on the firestore database.
- Contains a small segment defining the user preferred theme (Defaults to light mode).
- Validations towards the login and connection towards firebase, and an (unreliable) internet checking function (found in Validations.kt).

### DashboardActivity.kt -> dashboard.xml
- Inherited from the previous Amazon code segment (P1 batch).
- Links to a [LookerDocument](https://enhance-bdc3f.web.app/) visible in the Web Application, with blood pressure readings.
- Not currently used in the application, but maintained for posterity. Requires association to a patientID (Done in the code) to find specifics of the patient recorded.
- Outdated and needs fixing before use.

### SimpleDashboardActivity.kt -> activity_simple_dashboard.xml
- Features a simple dashboard stemming from P4 batch.
- Calls the ```visits``` collection within the respective ```patients``` in firebase, creating a list and sorting the list according to the date, and calls the systolic/diastolic functions to display the 3 most recent records.
``` kotlin
    lineChart = findViewById(R.id.syslineChart)
    setupLineChart()
    diastolicLineChart = findViewById(R.id.diastolicLineChart)
    setupDiastolicLineChart()
```
- Capable of printing out the records with the print button, sending users to a separate menu to either save the result as a pdf or connect to a printer to print the records.
- Validations to prevent users from printing with no records, and displaying an error message within the graphs to show no records.

### Diag.kt/HistoryData.kt
- Classes used to organise and define data.
- Diag is used to collect the date, average home bp (sys/dia), clinic bp, and the target home bp. Target clinic bp is fetched from adding +5 to the target home bp. Used by DiagnosePatient.kt.
- HistoryData is used to collect the ```visits``` collection within the respective ```patients``` in firebase, organising the data associated with the record in a list. Used in anywhere that involves visits, namely RecommendationActivity.kt, HistoryActivity.kt, SimpleDashboardActivity.kt, and DashboardActivity.kt.
- HistoryAdapter uses information from HistoryData, but does not call HistoryData directly.
