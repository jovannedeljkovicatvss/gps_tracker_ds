# gps_tracker_ds
Stabilan_Build_06.11.2025

# GPS Tracker DS

**Stabilna Android aplikacija za praćenje lokacije, upravljanje rutama i deljenje tačaka u realnom vremenu.**

## 📱 Funkcionalnosti

- 🔐 Login i registracija korisnika
- 🗺️ Prikaz mape sa trenutnom lokacijom (OpenStreetMap)
- 📍 Dodavanje, uređivanje i brisanje tačaka
- 🧭 Snimanje i brisanje ruta
- 📤 Izvoz ruta u deljiv format
- 📡 Praćenje drugog uređaja u realnom vremenu
- 🔎 Zoom na trenutnu lokaciju klikom na dugme "Moja lokacija"

## 🧪 Tehnologije

- **Kotlin + Jetpack Compose**
- **Room Database** za lokalno skladištenje
- **OpenStreetMap** integracija
- **MVVM arhitektura** sa modularnim pristupom
- **Gradle** build sistem (klasični `.gradle` fajlovi)


## 🧠 Inspiracija

Aplikacija je vizuelno i funkcionalno inspirisana iCar GPS aplikacijom, sa fokusom na modularnost, stabilnost i edukativnu vrednost za buduće generacije studenata.

## 🧑‍💻 Autor

Jovan Nedeljković — student, istraživač i praktičar koji kombinuje preciznost, vizuelnu jasnoću i edukativni pristup u svakom projektu.

---
## ZIP struktura

gps_tracker_ds/
├── .gitignore
├── README.md
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── .idea/
│   ├── codeStyles/
│   ├── libraries/
│   ├── vcs.xml
│   ├── misc.xml
│   ├── modules.xml
│   ├── workspace.xml
│   └── ...
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── java/
            │   └── com/
            │       └── jovannedeljkovicatvss/
            │           └── gps_tracker_ds/
            │               ├── MainActivity.kt
            │               ├── data/
            │               │   ├── model/
            │               │   │   └── User.kt
            │               │   └── repository/
            │               │       └── LocationRepository.kt
            │               ├── domain/
            │               │   └── usecase/
            │               │       └── TrackLocationUseCase.kt
            │               ├── ui/
            │               │   ├── login/
            │               │   │   └── LoginScreen.kt
            │               │   ├── map/
            │               │   │   └── MapScreen.kt
            │               │   └── components/
            │               │       └── LocationButton.kt
            │               ├── util/
            │               │   └── LocationUtils.kt
            │               └── navigation/
            │                   └── AppNavigation.kt
            └── res/
                ├── layout/
                │   └── activity_main.xml
                ├── values/
                │   ├── colors.xml
                │   ├── strings.xml
                │   └── themes.xml
                └── drawable/
                    └── ic_location.xml

