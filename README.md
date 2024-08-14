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
- Capable of printing out the records with the print button, sending users to a separate menu to either save the result as a pdf or connect to a printer to print the records.
- Validations to prevent users from printing with no records, and displaying an error message within the graphs to show no records.

### Diag.kt/HistoryData.kt
- Classes used to organise and define data.
- Diag is used to collect the date, average home bp (sys/dia), clinic bp, and the target home bp. Target clinic bp is fetched from adding +5 to the target home bp. Used by DiagnosePatient.kt.
``` kotlin
Diag(date: String?, avgSysBP: Long?, avgDiaBP: Long?, clinicSys: Long?, clinicDia: Long?, targetHomeSys: Long?, targetHomeDia: Long?) 
```
- HistoryData is used to collect the ```visits``` collection within the respective ```patients``` in firebase, organising the data associated with the record in a list. Used in anywhere that involves visits, namely RecommendationActivity.kt, HistoryActivity.kt, SimpleDashboardActivity.kt, and DashboardActivity.kt.
    - Note: When fetching from firestore database, only Long values are accepted, not Int. You may convert them after fetching the data, but it's better to work with Long unless required.
``` kotlin
HistoryData(date: String?, dateFormatted: String?, avgSysBP: Long?, avgDiaBP: Long?, homeSysBPTarget: Long?, homeDiaBPTarget: Long?, clinicSysBPTarget: Long?, clinicDiaBPTarget: Long?, clinicSysBP: Long?, clinicDiaBP: Long?, scanRecordCount: Long?)
```
- HistoryAdapter uses information from HistoryData, but does not call HistoryData directly.

### DiagnosePatient.kt 
- The primary code for the recommendation algorithm, and minor uses in the print function in SimpleDashboardActivity.kt and ProfileActivity.kt
- Features functions to maintain the printing result in English regardless of the language used in the app.
#### Functions
``` kotlin
// Sorts patient visits in ProfileActivity and used to determine recent "BP Stage". Returns a list of the Diag object.
fun sortPatientVisits(documents: QuerySnapshot) : List<Diag>

// Returns the resource ID of red, yellow and green based on the patient's systolic/diastolic relative to the respective target BP.
fun colourSet(context: Context, bp: Long, targetBP: Long): Int

// Shows the relevant recommendation based on the blood pressure of the patient (Optimal/Suboptimal).
fun showRecommendation(context: Context, optimal: String): String

// Overload of the above function, but sets the string value to strictly English, or any other future languages if added.
fun showRecommendation(context: Context, optimal: String, locale: String): String

// Displays controlled/uncontrolled bp based on the input of the systolic/diastolic and their respective target values.
fun bpControlStatus(context: Context, recentSys: Long, recentDia: Long, targetSys: Long, targetDia: Long): String

// Displays well controlled/suboptimum based on the current existing hypertension level fetched from hypertensionStatus()
fun bpControlStatus(context: Context, hypertensionLevel: String): String

// Displays the BP stage based on the average home BP and clinic BP, and their respective target values. Target clinic BP is fetched from adding +5 to target home BP. 
fun hypertensionStatus(context: Context, avgHomeSys: Long, avgHomeDia: Long, clinicSys: Long, clinicDia: Long, targetHomeSys: Long, targetHomeDia: Long): String

// Sets the date locale to english, and returning the appropriate date values. Used in SimpleDashboardActivity strictly.
fun dateLocale(context: Context, dateTime: String, locale: String): String
```

### HistoryActivity.kt -> activity_history.xml
- Responsible for displaying the ```visits``` collection for the respective ```patient```.
- Provides access to all past results, and displays an error message if none are found.
- Uses HistoryAdapter.kt to handle and display each individual item's values.
#### Functions
``` kotlin
// Not a reusable function, but houses the most important code in this area. Uses a bundle called with intent.extras on the next page (RecommendationActivity.kt) to send relevant information to the next page.
override fun onItemClick(position: Int) {
    '...'
    bundle.putInt("avgSysBP", avgSysBP.toInt())
    bundle.putInt("avgDiaBP", avgDiaBP.toInt())
    bundle.putInt("clinicSysBP", clinicSysBP.toInt())
    bundle.putInt("clinicDiaBP", clinicDiaBP.toInt())
    bundle.putString("date", date)
    bundle.putInt("historyItemPosition", position)
    bundle.putInt("scanRecordCount", recordCount.toInt())
    // Not used and is leftover of previous batches, but could be useful in specific cases.
    bundle.putString("Source", "History")
}
```

### HistoryAdapter.kt
- Calls ```onItemClick()``` in HistoryActivity to direct users to RecommendationActivity.
- Uses a RecyclerView to display the date, average home BP, clinic BP, and both target BPs.

### ScanActivity.kt -> activity_scan.xml
- The page where users can pick their different scan options.
- The primary area where the scanning image is sent and analysed to be used in VerifyScanActivity.
- Defines the OCR and much of the scanning filters and processing of data.
#### Functions
```kotlin
// Responsible for asking permissions for camera/gallery on first use. If successful, calls startCameraWithoutUri()
fun onClickRequestPermission()

// Launches the crop function of the scan, while fetching the image from their respective source.
fun startCameraWithoutUri(includeCamera: Boolean, includeGallery: Boolean)

// Holds the custom cropped image, and calls handleCropImageResult() if successfully cropped
val customCropImage = registerForActivityResult(CropImageContract())

// Handles the cropped image and parses the Uri of the new cropped image. If successful, calls processDocumentImage()
fun handleCropImageResult(uri: String)

// Processes the cropped image to look for scannable items with Google Vision. If successful, calls processDocumentTextBlock()
fun processDocumentImage()

// Handles the OCR text results from the image. Changes common filtering mistakes into numbers, while filtering out invalid values and maps the results to a systolic/diastolic list to be sent to VerifyScanActivity.
// Calls navigateToVerifyScanActivity() with the lists, and a check for Seven Day Scan/General Scan.
fun processDocumentTextBlock(result: FirebaseVisionDocumentText)

// Called in processDocumentTextBlock() to extract words, which are displayed through the console for debugging. Returns the raw list of results from the scan.
fun extractWordsFromBlocks(blocks: List<FirebaseVisionDocumentText.Block>): MutableList<FirebaseVisionDocumentText.Word>

// Called in processDocumentTextBlock() to process the numbers that are extracted from the scan. Returns a corrected list of numbers by modifying the variable.
fun processNumbers(numbers: List<Int>, sysBPList: MutableList<String>, diaBPList: MutableList<String>)

// Called in processDocumentTextBlock() to fix common errors in scanning. Legacy code from the previous batches, but still vital as a failsafe.
fun fixCommonErrors(sysBPList: MutableList<String>, diaBPList: MutableList<String>)

// Sends the results of the scan to VerifyScanActivity, with the use of a bundle. Holds a check for Seven Day Scan/General Scan
fun navigateToVerifyScanActivity(sysBPList: MutableList<String>, diaBPList: MutableList<String>, sevenDay: Boolean)

// Called when attempting to open a scan. If the permissions are not granted, display an error message, while allow the users to continue if they have approved.
// Only affects the first time click on scans, unless the user has not granted relevant permissions.
val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission())
```

### EditProfileActivity.kt -> activity_edit_patient.xml
- Allows users to edit the patient information other than their ID.
- Connection to firebase and relational database systems.
- Uses many functions present in ProfileActivity.
    - ```retrievePatient()```, ```updateUIWithPatientData()```, ```loadImageFromUrl()```
#### Functions
``` kotlin
// Loads the patient data into the fields for editing.
fun loadPatientData()

// Updates the patient with the new information, and directs the user back to the profile page.
fun updatePatient()
```

### ProfileActivity.kt -> activity_profile.xml
- Fetches the patient from firebase and displays the information.
#### Functions
``` kotlin
// Retrieves the patient info based on the ID provided in MainActivity.
fun retrievePatient(patientID: String)

// Updates the UI with the patient data. Self explanatory.
fun updateUIWithPatientData(document: DocumentSnapshot, patientID: String)

// Self explanatory. Called in updateUIWithPatientData().
fun loadImageFromUrl(imageUrl: String)

// Self explanatory. Deletes the current patient, and directs the user back to MainActivity.
fun deletePatient()
```
