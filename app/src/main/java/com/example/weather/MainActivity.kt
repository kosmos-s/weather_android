package com.example.weather

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.weather.ui.theme.WeatherTheme
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                getCurrentLocation()
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                getCurrentLocation()
            }
            else -> {
                Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var onLocationReceived: ((Double, Double) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherTheme {
                WeatherApp(
                    onRequestLocation = { callback ->
                        onLocationReceived = callback
                        requestLocationPermission()
                    }
                )
            }
        }
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            else -> {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    onLocationReceived?.invoke(it.latitude, it.longitude)
                } ?: run {
                    Toast.makeText(this, "위치를 가져올 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherApp(onRequestLocation: ((Double, Double) -> Unit) -> Unit) {
    var cityInput by remember { mutableStateOf("Seoul") }
    var weatherData by remember { mutableStateOf<WeatherData?>(null) }
    var forecastData by remember { mutableStateOf<List<ForecastItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    val API_KEY = BuildConfig.OPENWEATHER_API_KEY

    if (API_KEY.isBlank()) {
        Text("날씨 API 키가 설정되지 않았습니다. README의 키 설정 방법에 따라 키를 지정한 뒤 다시 빌드하세요.")
        return
    }

    LaunchedEffect(Unit) {
        scope.launch {
            fetchWeather("Seoul", API_KEY) { data, error ->
                weatherData = data
                errorMessage = error
            }
            fetchForecast("Seoul", API_KEY) { forecast, error ->
                forecastData = forecast
                if (error.isNotEmpty()) errorMessage = error
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFE3F2FD)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 검색 및 위치 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = { cityInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("도시 이름 입력") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Button(
                    onClick = {
                        if (cityInput.isNotBlank()) {
                            isLoading = true
                            scope.launch {
                                fetchWeather(cityInput, API_KEY) { data, error ->
                                    weatherData = data
                                    errorMessage = error
                                    isLoading = false
                                }
                                fetchForecast(cityInput, API_KEY) { forecast, error ->
                                    forecastData = forecast
                                }
                            }
                        }
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "검색")
                }

                Button(
                    onClick = {
                        isLoading = true
                        onRequestLocation { lat, lon ->
                            scope.launch {
                                fetchWeatherByCoords(lat, lon, API_KEY) { data, error ->
                                    weatherData = data
                                    errorMessage = error
                                    isLoading = false
                                    data?.let { cityInput = it.cityName }
                                }
                                fetchForecastByCoords(lat, lon, API_KEY) { forecast, error ->
                                    forecastData = forecast
                                }
                            }
                        }
                    },
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "현재 위치")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 탭 선택
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("현재 날씨") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("5일 예보") }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 로딩 또는 에러 표시
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage.isNotEmpty() -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2))
                    ) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFFC62828)
                        )
                    }
                }
                selectedTab == 0 && weatherData != null -> {
                    WeatherContent(weatherData!!)
                }
                selectedTab == 1 && forecastData.isNotEmpty() -> {
                    ForecastContent(forecastData)
                }
            }
        }
    }
}

@Composable
fun WeatherContent(data: WeatherData) {
    // 메인 날씨 카드
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(getWeatherGradient(data.icon))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${data.cityName}, ${data.country}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${data.temp.toInt()}°C",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = data.description,
                    fontSize = 20.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "체감: ${data.feelsLike.toInt()}°C",
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 상세 정보 카드
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "상세 정보",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121)
            )

            Spacer(modifier = Modifier.height(16.dp))

            WeatherDetailRow("💧", "습도", "${data.humidity}%")
            Spacer(modifier = Modifier.height(12.dp))

            WeatherDetailRow("🌡️", "기압", "${data.pressure}hPa")
            Spacer(modifier = Modifier.height(12.dp))

            WeatherDetailRow("💨", "풍속", "${data.windSpeed}m/s")
            Spacer(modifier = Modifier.height(12.dp))

            WeatherDetailRow("🌅", "일출", data.sunrise)
            Spacer(modifier = Modifier.height(12.dp))

            WeatherDetailRow("🌇", "일몰", data.sunset)
        }
    }
}

@Composable
fun ForecastContent(forecast: List<ForecastItem>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        forecast.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = item.date,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121)
                        )
                        Text(
                            text = item.time,
                            fontSize = 14.sp,
                            color = Color(0xFF757575)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${item.temp.toInt()}°C",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                        Text(
                            text = item.description,
                            fontSize = 14.sp,
                            color = Color(0xFF757575)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherDetailRow(emoji: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = emoji,
            fontSize = 24.sp,
            modifier = Modifier.width(40.dp)
        )

        Text(
            text = "$label: $value",
            fontSize = 16.sp,
            color = Color(0xFF212121)
        )
    }
}

fun getWeatherGradient(icon: String): Brush {
    return when {
        icon.contains("01") -> Brush.linearGradient(
            colors = listOf(Color(0xFFFFA726), Color(0xFFFF6F00))
        )
        icon.contains("02") || icon.contains("03") -> Brush.linearGradient(
            colors = listOf(Color(0xFF90CAF9), Color(0xFF42A5F5))
        )
        icon.contains("04") -> Brush.linearGradient(
            colors = listOf(Color(0xFF78909C), Color(0xFF546E7A))
        )
        icon.contains("09") || icon.contains("10") -> Brush.linearGradient(
            colors = listOf(Color(0xFF5C6BC0), Color(0xFF3949AB))
        )
        icon.contains("11") -> Brush.linearGradient(
            colors = listOf(Color(0xFF455A64), Color(0xFF263238))
        )
        icon.contains("13") -> Brush.linearGradient(
            colors = listOf(Color(0xFF81D4FA), Color(0xFF29B6F6))
        )
        else -> Brush.linearGradient(
            colors = listOf(Color(0xFF64B5F6), Color(0xFF2196F3))
        )
    }
}

data class WeatherData(
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val pressure: Int,
    val description: String,
    val icon: String,
    val windSpeed: Double,
    val cityName: String,
    val country: String,
    val sunrise: String,
    val sunset: String
)

data class ForecastItem(
    val date: String,
    val time: String,
    val temp: Double,
    val description: String,
    val icon: String
)

suspend fun fetchWeather(
    city: String,
    apiKey: String,
    onResult: (WeatherData?, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val url = "https://api.openweathermap.org/data/2.5/weather?q=$city&units=metric&lang=kr&appid=$apiKey"
            val response = URL(url).readText()
            val jsonObj = JSONObject(response)

            val main = jsonObj.getJSONObject("main")
            val weather = jsonObj.getJSONArray("weather").getJSONObject(0)
            val wind = jsonObj.getJSONObject("wind")
            val sys = jsonObj.getJSONObject("sys")

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sunrise = timeFormat.format(Date(sys.getLong("sunrise") * 1000))
            val sunset = timeFormat.format(Date(sys.getLong("sunset") * 1000))

            val data = WeatherData(
                temp = main.getDouble("temp"),
                feelsLike = main.getDouble("feels_like"),
                humidity = main.getInt("humidity"),
                pressure = main.getInt("pressure"),
                description = weather.getString("description"),
                icon = weather.getString("icon"),
                windSpeed = wind.getDouble("speed"),
                cityName = jsonObj.getString("name"),
                country = sys.getString("country"),
                sunrise = sunrise,
                sunset = sunset
            )

            withContext(Dispatchers.Main) {
                onResult(data, "")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(null, "날씨 정보를 가져올 수 없습니다: ${e.message}")
            }
        }
    }
}

suspend fun fetchWeatherByCoords(
    lat: Double,
    lon: Double,
    apiKey: String,
    onResult: (WeatherData?, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&lang=kr&appid=$apiKey"
            val response = URL(url).readText()
            val jsonObj = JSONObject(response)

            val main = jsonObj.getJSONObject("main")
            val weather = jsonObj.getJSONArray("weather").getJSONObject(0)
            val wind = jsonObj.getJSONObject("wind")
            val sys = jsonObj.getJSONObject("sys")

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sunrise = timeFormat.format(Date(sys.getLong("sunrise") * 1000))
            val sunset = timeFormat.format(Date(sys.getLong("sunset") * 1000))

            val data = WeatherData(
                temp = main.getDouble("temp"),
                feelsLike = main.getDouble("feels_like"),
                humidity = main.getInt("humidity"),
                pressure = main.getInt("pressure"),
                description = weather.getString("description"),
                icon = weather.getString("icon"),
                windSpeed = wind.getDouble("speed"),
                cityName = jsonObj.getString("name"),
                country = sys.getString("country"),
                sunrise = sunrise,
                sunset = sunset
            )

            withContext(Dispatchers.Main) {
                onResult(data, "")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(null, "위치 기반 날씨를 가져올 수 없습니다: ${e.message}")
            }
        }
    }
}

suspend fun fetchForecast(
    city: String,
    apiKey: String,
    onResult: (List<ForecastItem>, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val url = "https://api.openweathermap.org/data/2.5/forecast?q=$city&units=metric&lang=kr&appid=$apiKey"
            val response = URL(url).readText()
            val jsonObj = JSONObject(response)
            val list = jsonObj.getJSONArray("list")

            val dateFormat = SimpleDateFormat("MM월 dd일 (E)", Locale.KOREAN)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val items = mutableListOf<ForecastItem>()

            for (i in 0 until minOf(list.length(), 40)) {
                val item = list.getJSONObject(i)
                val dt = item.getLong("dt") * 1000
                val main = item.getJSONObject("main")
                val weather = item.getJSONArray("weather").getJSONObject(0)

                items.add(
                    ForecastItem(
                        date = dateFormat.format(Date(dt)),
                        time = timeFormat.format(Date(dt)),
                        temp = main.getDouble("temp"),
                        description = weather.getString("description"),
                        icon = weather.getString("icon")
                    )
                )
            }

            withContext(Dispatchers.Main) {
                onResult(items, "")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(emptyList(), "예보 정보를 가져올 수 없습니다: ${e.message}")
            }
        }
    }
}

suspend fun fetchForecastByCoords(
    lat: Double,
    lon: Double,
    apiKey: String,
    onResult: (List<ForecastItem>, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val url = "https://api.openweathermap.org/data/2.5/forecast?lat=$lat&lon=$lon&units=metric&lang=kr&appid=$apiKey"
            val response = URL(url).readText()
            val jsonObj = JSONObject(response)
            val list = jsonObj.getJSONArray("list")

            val dateFormat = SimpleDateFormat("MM월 dd일 (E)", Locale.KOREAN)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val items = mutableListOf<ForecastItem>()

            for (i in 0 until minOf(list.length(), 40)) {
                val item = list.getJSONObject(i)
                val dt = item.getLong("dt") * 1000
                val main = item.getJSONObject("main")
                val weather = item.getJSONArray("weather").getJSONObject(0)

                items.add(
                    ForecastItem(
                        date = dateFormat.format(Date(dt)),
                        time = timeFormat.format(Date(dt)),
                        temp = main.getDouble("temp"),
                        description = weather.getString("description"),
                        icon = weather.getString("icon")
                    )
                )
            }

            withContext(Dispatchers.Main) {
                onResult(items, "")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(emptyList(), "예보 정보를 가져올 수 없습니다: ${e.message}")
            }
        }
    }
}