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
XML is vital to the frontend of the webpage, and where much of the dynamic nature of the project has been developed from, particularly affecting languages and themes. More details about strings and themes are down below.
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
#### (Dynamic) Strings and Language Options
- Every line of text in the file barring some rare exceptions (Seven Day Scan in VerifyScanActivity) all stem from the string.xml file under res/values/strings. This allows for quick translation of strings for multiple languages, but so far there's only two - Simplified/Traditional Chinese.
- To fetch strings from these files, the primary method is to call the ```getString()``` function, followed up with the resource ID of the string (R.string.app_name where app_name is the name of the string given in the strings.xml file).
    - Important: the ```getString()``` function only exists in Activity files. In order to use ```getString()``` outside of there, you can either use ResourcesHelper() object, and add in a ```getString()``` function within to get the strings. Refer to the code below, and use them in your .kt files. (Note: In some cases you can still call ```getString()``` normally from non-Activity files, but this is likely due to imports from an Activity file. Untested, but if this is proven to be more efficient, I recommend not using ResourcesHelper.)
     ``` kotlin
     // Normal Use Case in Activity
     getString(R.string.app_name)

     // Use Case outside of Activity
     object ResourcesHelper {
         fun getString(context: Context, resId: Int): String {
             return context.getString(resId)
         }

         // A function that gets a string with a variable within. Takes in any value that can be displayed in a string.
         // @StringRes ensures that the ID comes from strings.xml (I believe), but is not necessary.
         fun getString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
            return context.getString(resId, *formatArgs)
         }
     }
     
     // Context is always represented by 'this' without quotation marks.
     ResourcesHelper.getString(this, R.string.app_name)
     // This function is also present in normal getString() functions in activity files. For more specific uses with more inputs, you can change the function as needed.
     // Note: To add in variables, make sure to add them in your strings.xml string holder. '%1$s' is an example of a string input, but there are many different types that you can use (Personally not familiar with most).
     // <string name="enhance_recco_header">The following are the recommendations based on the %1$s scanned BP records:</string>
     ResourcesHelper.getString(this, R.string.app_name, "Hello World!")
     ``` 
- Translated string files are distinguished by a small flag next to the name of the file, along with a bracketed segment indicating the language, and the region if there's any. strings.xml is always the default language, if you wish to directly change the default language of the app.

    ![image](https://github.com/user-attachments/assets/24c34858-68ca-46c6-b773-1b89998d8bdb)

    In the resource files above, the language is indicated by the first two alphabets (zh) and the region is indicated by a regional tag and the two letter indicator (rCN meaning region-Mainland China). In code, you do not need to indicate the regional tag, and only needing a two letter indicator. Refer to LanguageActivity functions to learn more about how to use this to translate your app into more languages.
    - To add more languages, open up the translation editor within any strings.xml file (The popup above), and click on the globe with a plus and pick your language.
    - Note: While the system gives an error for not translating the string after adding new strings, the code will still run, defaulting to the normal strings.xml file. You can also continue using hardcoded strings if you wish to maintain a more static system of strings.

#### Colours and Themes
- In .xml files, the colour of certain elements can be set through dynamic values within a colors.xml file (Or any other res/values file really).
- In a similar light, theme files can hold colours of specific elements which change based on the active theme. This means that an element named 'text_light' would fetch from all existing theme files in res/values/themes, and be coloured differently if light theme contained white, and dark theme contained yellow. This concept is crucial in making different themes work well together in a dynamic way.
- You can always set the colours statically through their hex values, or statically through colors.xml files. color.xml is still useful dynamically, used for definiing the colours of themes, although they can be defined in the theme files themselves.
- Currently unclear on how to create more themes, but the app comes with a light/dark mode within, which is modified to allow a smooth experience UI wise.

![image](https://github.com/user-attachments/assets/a46014e1-d3dd-4994-8d1e-114a204348b4)
![image](https://github.com/user-attachments/assets/0dc15b78-3d43-4920-a28d-0f2d60b9b3f9)
Above, this is one example of the same colour name used in different theme files, with dark theme above, and light theme below. While light theme maintains the same orange for the primary buttons, the dark theme uses grey, with two different shades relevant to different buttons throughout the code. Pretty neat, right? Many more colours are defined through this method to make the theme dynamic, while also depending on a bigger primary colour scheme to fall back on if the element is not statically coded to have a colour.

- While I used 'colour' instead of 'color' in these examples, code wise 'color' is used.

## Primary File Structure

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
### VerifyScanActivity.kt -> activity_verify_scan.xml
- The page where users can verify and review their blood pressure readings after being scanned and processed. This activity also handles the calculation and validation of blood pressure records, allows for user interaction with the readings, and provides options to undo, swap, or remove entries.
- This activity also contains options for re-scanning, continuing the scan, or discarding the progress.
#### Functions
```kotlin

// Initializes the activity, sets up the UI elements, retrieves patient data, and loads any existing scan data.
override fun onCreate(savedInstanceState: Bundle?)

// Handles the back button press, redirecting the user to ScanActivity.
override fun onOptionsItemSelected(item: MenuItem): Boolean

// Continues the scan by passing the current data to ScanActivity.
fun continueScan()

// Prompts the user to rescan the records and passes the existing data back to ScanActivity.
fun rescanRecords()

// Discards the current progress and prompts the user with an error dialog.
fun discardProgress()

// Saves the current state of the blood pressure lists to enable the undo functionality.
private fun saveStateForUndo()

// Compares two lists of strings to check if they are identical.
private fun listsAreEqual(list1: MutableList<String>, list2: MutableList<String>): Boolean

// Reverts the last change made to the blood pressure lists using the saved undo stack.
fun undo()

// Refreshes the views to reflect the current state of the blood pressure lists after any changes.
private fun refreshViews()

// Performs validation on the blood pressure readings to identify any errors or anomalies.
private fun postScanValidation()

// Sets an error message on a TextInputLayout if the validation fails.
private fun setError(inputLayout: TextInputLayout, message: String?)

// Validates the input fields for systolic and diastolic blood pressure readings.
private fun validateFields(): Boolean

// Retrieves the target blood pressure values from the UI.
private fun getBPTarget()

// Calculates the average blood pressure from the current readings.
private fun calcAvgBP()

// Calculates the average blood pressure over a seven-day period using specific rules for choosing readings.
private fun calcSevenDayAvgBP()

// Populates the UI with the existing blood pressure readings for a seven-day scan.
private fun sevenDayCheck()

// Ensures that the blood pressure lists have a minimum of 28 elements, padding with default values if necessary.
private fun ensureListSize(list: MutableList<String>, targetSize: Int)

// Adds a new row to the UI for entering or displaying a blood pressure reading.
@SuppressLint("SetTextI18n")
private fun addRow(
    sysBP: String?,
    diaBP: String?,
    isSevenDayCheck: Boolean = false,
    day: Int = -1,
    time: Int = -1,
    showHeader: Boolean = false
)

// Adds a divider to visually separate old and new records in the UI.
private fun addDivider()
```
### ModalBottomSheet.kt -> bottom_sheet_verify_scan.xml
- A bottom sheet dialog fragment that provides additional options to the user, such as continuing the scan, re-scanning, undoing the last change, or discarding the progress.
#### Functions
```kotlin
// Inflates the bottom sheet view and sets up the click listeners for each option.
override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?

// Sets up the view elements and handles click events for the options in the bottom sheet.
override fun onViewCreated(view: View, savedInstanceState: Bundle?)

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

### RegistrationActivity.kt -> activity_registration.xml
- Registers the patient into firebase.
- Contains all relevant information to a patient, barring clinic ID which is fetched from the staff that is logged in.
#### Functions
``` kotlin
// Self explanatory. Called after validateFields() returns true.
fun registerPatient()
```

### RecommendationActivity.kt -> activity_recommendation.xml
- Contains all of the functions relating to the recommendation page, accessed through HistoryActivity.
- Calls ```visits``` collection from ```patients```, as well as calling ```intent.extras``` from HistoryActivity to fetch the values associated with the records.
    - Both groups of data have duplicate values, but the ```visits``` collection takes most of the use cases. The ```intent.extras``` is primarily used for the ```bundlePosition``` variable, to associate the items with the ```sortedHistory``` list created in ```visits```.
    - Editor's note: Only ```bundlePosition``` is used, other data can be removed as they stem from the ```visits``` collection.
 
### AboutAppActivity.kt -> activity_about.xml, UserGuideActivity.kt -> activity_user_guide.xml
- Only the .xml is used in the app settings, the .kt file only has a code that redirects users back to settings if the back button is pressed.

### UserGuides.kt
- A file that holds a class, likely used in a previous batch (There was some code that created a dynamic user guide with dropdowns in UserGuideActivity, but is long unfunctional).
- Seemingly safe to remove.

### LanguageActivity.kt -> activity_language.xml
- The main area where languages are managed and set.
- Lists a bunch of languages and allows the user to pick their preferred choice, with simple validation.
#### Functions
``` kotlin
// Non-regional language setting function. Sets the language based on the 2 letter locale input given.
fun languageBuilder(context: Context, curLanguage: String, locale: String)

// Regional language setting function. Same as above, but also takes in a 2 letter regional input. Only appropriate for languages with regional variants (eg. Simplified/Traditional Chinese)
fun languageBuilder(context: Context, curLanguage: String, locale: String, region: String)
```

### PrivacyStatementActivity.kt -> activity_privacy_statement.xml, TermsOfUseActivity.kt -> activity_terms_of_use.xml
- Unused in code outside of the AndroidManifest.xml. Safe to delete.

### SettingsActivity.kt -> activity_settings.xml
- The settings menu that leads to many areas associated to settings, ie. ```ThemeActivity```, ```LanguageActivity```, ```AboutAppActivity```, and ```UserGuideActivity```

### ThemeActivity.kt -> activity_theme.xml
- Allows the user to change their theme between dark and light mode, where light mode is the default.
#### Functions
``` kotlin
// Uses getSharedPreferences to hold the user's choice, and calls a function within LoginActivity to update the theme.
// The relevant code segment can be moved to SplashScreenActivity for a more smooth transition, but for now it remains in LoginActivity for ease of access.
fun saveThemePreference(isDarkMode: Boolean)
```

### ErrorHandling.kt
- The primary area where error message dialogs are built in. Mostly used for dynamic coding and allowing users to retry their previous actions.
``` kotlin
// Called when internetConnectionCheck in Validations returns a false value.
fun noInternetErrorDialog(context: Context)

// The overall function is used when firebase is involved. This overload function reruns the inputted function after the user indicates a retry.
fun firebaseErrorDialog(context: Context, e: Exception, function:(String) -> Unit, string: String)

// Same as above, but calls the .get() function of a document.
fun firebaseErrorDialog(context: Context, e: Exception, document: DocumentReference)

// Same as above, but calls the StorageReference to fetch an image.
fun firebaseErrorDialog(context: Context, e: Exception, storage: StorageReference, photo: ByteArray)

// A generic builder for error dialogs, taking a title and message as an input, and returning a 'OK' button. 
fun errorDialogBuilder(context: Context, title: String, message: String)

// Same as above, but calls an Activity when 'YES' (PositiveButton) is indicated.
fun errorDialogBuilder(context: Context, title: String, message: String, activity: Class<*>)

// Same as above, but with a unique overload for a special icon.
fun errorDialogBuilder(context: Context, title: String, message: String, activity: Class<*>, icon: Int)

// Unique error dialog exclusive to EditprofileActivity.
fun errorClinicDialogBuilder(context: Context, title: String, message: String, activity: Class<*>)

// Self explanatory. Called in most files involving patient ID, but is currently incomplete in some files.
fun patientNotFoundInSessionErrorDialog(context: Context)

// Self explanatory. Called in ScanActivity.
fun ocrTextErrorDialog(context: Context)
```

### Validations.kt
- Primary area where validations and checks are handled. Incomplete at the moment.
#### Functions
``` kotlin
// Self explanatory. Called in most locations that require a connection to firebase. Functionally wonky, as it does not call on Activity runtime and takes a moment, while also being redundant, having a similar check in firebaseErrorDialog().
fun internetConnectionCheck(context: Context)
```

### MainActivity.kt -> activity_main.xml
- The main page after logging in with credentials. Also defines the patient ID in the session when searched for.
#### Functions
``` kotlin
// Clears patient info in the session, ie. when this page activity is resumed.
override fun onResume()

// Self explanatory.
private fun getPatientData(patientID: String)

// Saves patient ID in SecuredSharedPreferences.
private fun savePatientData(patientID: String)
```

### SplashScreenActivity.kt -> activity_splash_screen.xml
- Responsible for initiating the splash screen of the app.
- The first page to run at runtime, as indicated in AndroidManifest.xml.
