package com.example.individualassignment_52

import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.individualassignment_52.ui.theme.IndividualAssignment_52Theme

//A viewmodel to retain the sensor data across any reconfigurations
class orientationViewModel: ViewModel() {
    //accel. data
    var ax by mutableStateOf(0f)
    var ay by mutableStateOf(0f)
    var az by mutableStateOf(0f)
    //mag. data
    var mx by mutableStateOf(0f)
    var my by mutableStateOf(0f)
    var mz by mutableStateOf(0f)
    //gyro. data
    var pitch by mutableStateOf(0f)
    var roll by mutableStateOf(0f)
    var time by mutableStateOf(0L)
}

class MainActivity : ComponentActivity(), SensorEventListener {
    //make vars for the sensor manager and all the sensors
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gyroscope: Sensor? = null

    //declare the viewmodel (it must be initialized  in onCreate() )
    private lateinit var ortn: orientationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //initialize viewModel
        ortn = ViewModelProvider(this)[orientationViewModel::class]

        // Initialize Sensor Manager and sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            IndividualAssignment_52Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MakeScreen(ax = ortn.ax, ay = ortn.ay, az = ortn.az,
                        mx = ortn.mx, my = ortn.my, mz = ortn.mz,
                        roll = ortn.roll, pitch = ortn.pitch)
                }
            }
        }
    }

    //set up the listener for each sensor on resume
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        //I'm trying to calculate total position based on rate of change and time.
        //That requires accurate measurements. As such, I used a higher sensor rate for the
        //gyroscope.
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    //unregister listener on pause
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    //a lot to do if the sensor changes...
    override fun onSensorChanged(event: SensorEvent?) {
        var aPitch: Double = 0.0
        var aRoll: Double = 0.0
        var gPitch: Double = 0.0
        var gRoll: Double = 0.0
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    //on acc. update, just write the data to the viewModel
                    ortn.ax = it.values[0]
                    ortn.ay = it.values[1]
                    ortn.az = it.values[2]
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    //on mag. update, write data to viewModel too
                    ortn.mx = it.values[0]
                    ortn.my = it.values[1]
                    ortn.mz = it.values[2]
                }
                Sensor.TYPE_GYROSCOPE -> {
                    //ALOT needs to happen when the gyroscope shifts
                    //Not only do I need to check the readings, but I also need to
                    //calculate position shift from the timestamp of the event.
                    val p = it.values[2]
                    val r = it.values[0]
                    if (ortn.time == 0L) ortn.time = it.timestamp
                    //The gyro measures in rad/sec but the timestamp is in
                    //ns. So I need to divide by 10^9
                    val timeDiff = (it.timestamp - ortn.time) / 1000000000
                    ortn.time = it.timestamp
                    //convert the shift to degrees
                    gPitch = Math.toDegrees((p * timeDiff).toDouble())
                    gRoll = Math.toDegrees((r * timeDiff).toDouble())

                    //determine rough orientation based on existing accelerometer data
                    //(trigonometry time)
                    aRoll = Math.toDegrees(
                        Math.atan2(
                            ortn.ay.toDouble(),
                            Math.sqrt((ortn.ax * ortn.ax + ortn.az * ortn.az).toDouble())
                        )
                    )
                    aPitch = Math.toDegrees(
                        Math.atan2(
                            ortn.ax.toDouble(),
                            Math.sqrt((ortn.ay * ortn.ay + ortn.az * ortn.az).toDouble())
                        )
                    )

                    /*
                    THIS WAS GIVEN BY CHATGPT.
                    I was having trouble figuring out how to combine both the accelerometer and gyroscope
                    data to create an accurate measurement. The acc. keeps it relative to the ground and
                    doesn't skew but it's not precise and its slower. The gyro. is fast but has no reference
                    point and skews. StackOverflow answers were hard to follow. I asked AI for how to
                    calculate the final position and it recommended this math.
                    Essentially, we're taking a weighted average of the positions as determined
                    by both sensors. This particular ratio was suggested and tested well.
                     */
                    ortn.roll = ((0.98 * (ortn.roll + gRoll)) + (0.02 * aRoll)).toFloat()
                    ortn.pitch = ((0.98 * (ortn.pitch + gPitch)) + (0.02 * aPitch)).toFloat()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}

//enum class for the current screen of the app being selected.
//Not really needed (ints would work fine) but I wanted to toy with the enum class a bit.
enum class screenSelection{
    MENU,
    COMPASS,
    LEVEL
}

//Main function to show screen.
@Composable
fun MakeScreen(ax: Float, ay: Float, az: Float,
               mx: Float, my: Float, mz: Float,
               roll: Float, pitch: Float) {

    //store the screen choice to determine what to show
    var screenChoice by rememberSaveable { mutableStateOf(screenSelection.MENU) }

    Scaffold(){innerPadding ->
        //in the menu, show a button for each tool and prompt a selection
        if(screenChoice == screenSelection.MENU) {
            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "Select a tool:",
                    fontStyle = FontStyle.Italic,
                    fontSize = 30.sp
                )
                //buttons simply update the screen selection. On recomposition, the tool is shown.
                Button(
                    onClick = {screenChoice = screenSelection.COMPASS},
                    modifier = Modifier
                        .height(height = 100.dp)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "COMPASS",
                        fontSize = 20.sp
                    )
                }
                Button(
                    onClick = {screenChoice = screenSelection.LEVEL},
                    modifier = Modifier
                        .height(height = 100.dp)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "LEVEL",
                        fontSize = 20.sp
                    )
                }
            }
        //show the compass
        } else if(screenChoice == screenSelection.COMPASS) {
            MakeCompass(ax, ay, az, mx, my, mz, innerPadding, {screenChoice = screenSelection.MENU})
        //show the level
        } else if(screenChoice == screenSelection.LEVEL) {
            MakeLevel(pitch = pitch, roll = roll, innerPadding = innerPadding,
                back = {screenChoice = screenSelection.MENU})
        }
    }
}

//function to display the level tool.
@Composable
fun MakeLevel(roll: Float, pitch: Float, innerPadding: PaddingValues, back: ()->Unit){
    //Needed info to try to adapt to window orientation shift
    var sz by remember { mutableStateOf(Size.Zero)}
    val density = LocalDensity.current.density
    val windowInfo = calculateCurrentWindowInfo()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .onGloballyPositioned {coords ->
                sz = coords.size.toSize()
            }
    ) {
        //button to return to menu
        Button(
            onClick = {back()},
            modifier = Modifier
                .align(Alignment.BottomStart)
        ) {
            Text(
                text = "Back",
                fontStyle = FontStyle.Italic
            )
        }

        /*
        The design is as follows:
        A sort of "beads on strings" image where there are two circles. One slides on a
        horizontal line and one a vertical line. The lines cross exactly in the middle
        of the screen. One circle is wider but hollow and the other is filled but exactly
        the size of the hollow. The circles shift back and forth as the screen tilts, as it
        affected by gravity. When the screen is totally flat on the ground, they both sit in
        the middle of the screen and overlap to make a single solid circle.
         */

        VerticalDivider(
            thickness = 10.dp,
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.Center)
        )
        Divider(
            thickness = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        )
        //display the roll and pitch numbers directly as well.
        Text(
            text = String.format("Pitch: %.2f", pitch),
            fontSize = 24.sp
        )
        Text(
            text = String.format("Roll: %.2f", roll),
            fontSize = 24.sp,
            modifier = Modifier
                .offset(x = 0.dp, y = 20.dp)
        )

        //horizontal indicator
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    x = if(windowInfo.orientation == Orientation.PORTRAIT)
                            (-1*((sz.width / 45) * pitch)/density).dp
                        else
                            (((sz.height / 45) * roll)/density).dp,
                    y = 0.dp
                )
        ){
            drawCircle(
                color = Color.Green,
                center = center,
                radius = 50.dp.toPx(),
                style = Stroke(width = 8.dp.toPx())
            )
        }
        //vertical indicator
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    y = if(windowInfo.orientation == Orientation.PORTRAIT)
                            (((sz.height / 45) * roll)/density).dp
                        else
                            (((sz.width / 45) * pitch)/density).dp,
                    x = 0.dp
                )
        ) {
            drawCircle(
                color = Color.Green,
                center = center,
                radius = 46.dp.toPx(),
            )
        }
    }
}

//function to make the compass
@Composable
fun MakeCompass(ax: Float, ay: Float, az: Float,
                mx: Float, my: Float, mz: Float,
                innerPadding: PaddingValues, back: ()->Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        //SensorManager has built-in class methods to calculate orientation
        //data from magnetometer and accelerometer data. Pretty cool.
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        //this creates a rotation matrix from the raw sensor data.
        //I do not know what a rotation matrix is, but it's needed for the full calculation.
        SensorManager.getRotationMatrix(
            rotationMatrix, null,
            floatArrayOf(ax, ay, az),
            floatArrayOf(mx, my, mz)
        )
        //This takes that rotation matrix and makes a set of yaw, pitch, and azimuth
        //values. The trick is that they're all relative to the north direction,
        //not the phone's axes!
        SensorManager.getOrientation(rotationMatrix, orientation)

        //Take azimuth and convert from radians to degrees.
        val azimuth = Math.toDegrees(orientation[0].toDouble())

        //By default the azimuth is given in a range of -180 to 180.
        //I want it from 0 to 360 so I convert it here.
        val convertedAzimuth = if (azimuth >= 0) azimuth else 360 + azimuth

        /*
        Okay, okay: this next bit of math was GENERATED IN PART BY CHATGPT.
        I was struggling with the issue of the animation "jumping" whenever the
        azimuth passed over the 0-360 bound as the animation made it rotate a full
        360 degrees to the other side instantly. This is what is happening:
        - store the previous azimuth value
        - get the difference between the previous and current azimuth
            - this needs to be the MINIMUM difference. So if the two values
            are 359 and 1, the result needs to be 2 and not 358.
            - SO: by taking the actual difference, adding 540, then taking
            modulo of 360, we get a number that:
                - must be positive
                - must be between 0 and 180
            - by then subtracting 180, we get a number that must be between -180
            and 180.
            - This value is the smallest degree shift between prevAzimuth and the
            current azimuth.
        - add the difference to the previous azimuth value to get the new position
            - this value will not necessarily be the same as the current azimuth. It
            could also be a value greater than 360.
         */
        var prevAzimuth by remember { mutableStateOf(convertedAzimuth) }
        val delta = ((convertedAzimuth - prevAzimuth + 540) % 360) - 180
        prevAzimuth = (prevAzimuth + delta)
        /*
        Thus ends the chapGPT code. It's not a long section, I know. A lot of
        comments for three lines. But it's a very important section that should be
        identified accordingly.
         */

        //Make the animation value for the rotation angle
        val rotationAnimation by animateFloatAsState(
            targetValue = -1 * prevAzimuth.toFloat(),
        )
        //We don't want the rotation value to potentially grow forever. Once the
        //work is done, take the modulo to keep it within 0-360 range.
        prevAzimuth %= 360

        //get the angle of rotation for display to the user. It's the inverse of the
        //compass face rotation angle.
        val angle = convertedAzimuth
        //Sure, why not? Let's also give the general bearing.
        val bearing = when{
            angle >= 0 && angle < 30 -> "N"
            angle >= 30 && angle < 60 -> "NE"
            angle >= 60 && angle < 120 -> "E"
            angle >= 120 && angle < 150 -> "SE"
            angle >= 150 && angle < 210 -> "S"
            angle >= 210 && angle < 240 -> "SW"
            angle >= 240 && angle < 300 -> "W"
            angle >= 300 && angle < 330 -> "NW"
            else -> "N"
        }

        //display the angle and bearing to the user
        Text(
            text = String.format("%.1f\u00B0 $bearing", angle),
            textAlign = TextAlign.Center,
            fontSize = 48.sp
        )

        //Use a box to overlap a needle image over a compass image
        Box(modifier = Modifier.aspectRatio(1f)) {
            Image(
                painter = painterResource(R.drawable.compass_face2),
                contentDescription = "Compass face",
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )
            Image(
                painter = painterResource(R.drawable.compass_needle2),
                contentDescription = "Compass needle",
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight(0.7f)
                    .aspectRatio(1f)
                    .offset(x = 6.dp, y = (-6).dp)
                    .rotate(rotationAnimation)  //make the needle rotate

            )
        }

        //back button
        Button(
            onClick = {back()},
        ) {
            Text(
                text = "Back",
                fontStyle = FontStyle.Italic
            )
        }
    }
}

//Borrowed from example code. Retrieves stats about current device window.
@Composable
fun calculateCurrentWindowInfo(): WindowInfo {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val orientation = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        Orientation.PORTRAIT
    } else {
        Orientation.LANDSCAPE
    }

    return WindowInfo(
        widthDp = screenWidth,
        heightDp = screenHeight,
        orientation = orientation
    )
}
//Borrowed from example code. Stores window stats.
data class WindowInfo(
    val widthDp: Int,
    val heightDp: Int,
    val orientation: Orientation
)
//Borrowed from example code. Represents screen orientation.
enum class Orientation {
    PORTRAIT,
    LANDSCAPE
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    IndividualAssignment_52Theme {
        MakeLevel(0f, 0f, PaddingValues(), {})
    }
}