# Deja todo listo para la demostracion:
#   0. MongoDB en localhost:27017 (si no esta arriba, lo levanta como proceso del usuario)
#   1. Microservicio REST de Prestamo (clase C, base propia) en el puerto 8082
#      y microservicio GraphQL de Libro y Ejemplar en el puerto 8081 (con login)
#   2. Cliente web (React) en http://localhost:5173 y lo abre en el navegador
#   3. App Flutter en el celular conectado por USB (adb reverse + instalar + abrir)
#   4. Ventana con los logs del servidor en vivo (operaciones de la web y la app)
#
# Se puede ejecutar varias veces: lo que ya esta corriendo no se vuelve a lanzar.
# Si se desconecta el cable del celular, basta con volver a ejecutarlo.
# Compatible con Windows PowerShell 5.1 (solo ASCII a proposito, por la codificacion).

$ErrorActionPreference = 'Continue'

$raiz     = Split-Path -Parent $PSScriptRoot
$servidor = Join-Path $raiz 'servidor-libreria-mongo'
$web      = Join-Path $raiz 'cliente-web-react'
$movil    = Join-Path $raiz 'cliente-movil-flutter'
$prestamos = Join-Path $raiz 'servicio-prestamos-rest'
$jar      = Join-Path $servidor 'target\ServidorLibreriaMongo-3.0.0.jar'
$jarPrestamos = Join-Path $prestamos 'target\ServicioPrestamosRest-1.0.0.jar'
$apk      = Join-Path $movil 'build\app\outputs\flutter-apk\app-release.apk'
$paquete  = 'com.libreria.cliente_movil_libreria'
$urlGraphQL = 'http://localhost:8081/graphql'
$urlSesion  = 'http://localhost:8081/api/sesion'
$urlPrestamos = 'http://localhost:8082/prestamos/healthCheck'
$urlWeb     = 'http://localhost:5173'

# Credenciales de la demostracion; se cambian con LIBRERIA_USUARIO y LIBRERIA_CLAVE.
$usuarioLogin = if ($env:LIBRERIA_USUARIO) { $env:LIBRERIA_USUARIO } else { 'profesor' }
$claveLogin   = if ($env:LIBRERIA_CLAVE) { $env:LIBRERIA_CLAVE } else { 'libreria' }
$credencial   = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${usuarioLogin}:${claveLogin}"))

function Paso($texto)  { Write-Host ''; Write-Host "==> $texto" -ForegroundColor Cyan }
function Bien($texto)  { Write-Host "    OK  $texto" -ForegroundColor Green }
function Aviso($texto) { Write-Host "    !!  $texto" -ForegroundColor Yellow }
function Falla($texto) { Write-Host "    XX  $texto" -ForegroundColor Red }

# Prueba directa de conexion al puerto (Get-NetTCPConnection falla en algunos equipos).
# Se prueban IPv4 e IPv6 porque Vite puede escuchar solo en ::1.
function Escuchando($puerto) {
    foreach ($direccion in @('127.0.0.1', '::1')) {
        $familia = if ($direccion -eq '::1') { 'InterNetworkV6' } else { 'InterNetwork' }
        $cliente = New-Object System.Net.Sockets.TcpClient($familia)
        try {
            $intento = $cliente.BeginConnect($direccion, $puerto, $null, $null)
            if ($intento.AsyncWaitHandle.WaitOne(400) -and $cliente.Connected) { return $true }
        } catch { } finally { $cliente.Close() }
    }
    return $false
}

function ServidorResponde {
    try {
        $r = Invoke-RestMethod -Uri $urlSesion -Headers @{ Authorization = "Basic $credencial" } -TimeoutSec 3
        return $null -ne $r.usuario
    } catch { return $false }
}

function PrestamosResponde {
    try {
        return (Invoke-RestMethod -Uri $urlPrestamos -TimeoutSec 3) -eq 'OK'
    } catch { return $false }
}

# True si falta el archivo o si algun fuente es mas nuevo que el (evita demostrar una version vieja).
function Desactualizado($archivo, [string[]]$fuentes) {
    if (-not (Test-Path $archivo)) { return $true }
    $fecha = (Get-Item $archivo).LastWriteTime
    $nuevo = Get-ChildItem -Path $fuentes -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -gt $fecha } | Select-Object -First 1
    return $null -ne $nuevo
}

function Esperar($descripcion, $segundos, [scriptblock]$condicion) {
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        if (& $condicion) { return $true }
        Start-Sleep -Milliseconds 700
    }
    Falla "$descripcion no respondio en $segundos s"
    return $false
}

# --------------------------------------------------------------------------------------------
Paso 'MongoDB (localhost:27017)'

if (Escuchando 27017) {
    Bien 'ya estaba corriendo'
} else {
    $servicio = Get-Service -Name 'MongoDB' -ErrorAction SilentlyContinue
    if ($servicio) {
        try { Start-Service 'MongoDB' -ErrorAction Stop } catch { Aviso 'el servicio MongoDB pide permisos de administrador; se usa un proceso del usuario' }
    }
    if (-not (Esperar 'MongoDB (servicio)' 5 { Escuchando 27017 })) {
        $mongod = (Get-Command mongod -ErrorAction SilentlyContinue).Source
        if (-not $mongod) {
            $mongod = Get-ChildItem 'C:\Program Files\MongoDB\Server\*\bin\mongod.exe' -ErrorAction SilentlyContinue |
                Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
        }
        if (-not $mongod) {
            Falla 'no se encontro mongod.exe: instalar con  winget install MongoDB.Server'
            exit 1
        }
        # Carpeta de datos: LIBRERIA_DATOS_MONGO, la de D:\01-Desarrollo si existe, o %LOCALAPPDATA%.
        $datos = $env:LIBRERIA_DATOS_MONGO
        if (-not $datos) {
            $junto = Join-Path (Split-Path -Parent $raiz) 'Bases-de-Datos\mongo-tercer-proyecto'
            if (Test-Path $junto) { $datos = $junto } else { $datos = Join-Path $env:LOCALAPPDATA 'TercerProyecto-Libreria\mongo' }
        }
        New-Item -ItemType Directory -Force (Join-Path $datos 'db'), (Join-Path $datos 'log') | Out-Null
        Start-Process -FilePath $mongod -WindowStyle Hidden -ArgumentList @(
            '--dbpath', "`"$(Join-Path $datos 'db')`"", '--logpath', "`"$(Join-Path $datos 'log\mongod.log')`"",
            '--logappend', '--bind_ip', '127.0.0.1', '--port', '27017')
        if (Esperar 'MongoDB' 30 { Escuchando 27017 }) { Bien "listo (datos en $datos)" } else { exit 1 }
    } else {
        Bien 'servicio MongoDB iniciado'
    }
}

# --------------------------------------------------------------------------------------------
Paso 'Microservicio REST de Prestamo (puerto 8082, base libreria_prestamos)'

$java = 'java'
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($jdk) { $java = Join-Path $jdk.FullName 'bin\java.exe' }
}
# Maven necesita JAVA_HOME con el JDK 17 aunque el java del PATH sea otro.
if ($java -ne 'java') { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java) }

if (PrestamosResponde) {
    Bien 'ya estaba corriendo'
} else {
    if (Desactualizado $jarPrestamos @((Join-Path $prestamos 'src\main'), (Join-Path $prestamos 'pom.xml'))) {
        Aviso 'compilando el microservicio de prestamos (1-2 min)...'
        Push-Location $prestamos
        & .\mvnw.cmd -q -DskipTests package
        Pop-Location
    }
    Start-Process cmd -WorkingDirectory $prestamos `
        -ArgumentList "/k chcp 65001 >nul && title Microservicio Prestamos - puerto 8082 && `"$java`" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -jar `"$jarPrestamos`""
    if (Esperar 'El microservicio de prestamos' 90 { PrestamosResponde }) { Bien "listo en $urlPrestamos" }
}

# --------------------------------------------------------------------------------------------
Paso 'Microservicio GraphQL de Libro y Ejemplar (puerto 8081, con login)'

if (ServidorResponde) {
    Bien 'ya estaba corriendo'
} else {
    if (Desactualizado $jar @((Join-Path $servidor 'src\main'), (Join-Path $servidor 'pom.xml'))) {
        Aviso 'compilando el servidor (1-2 min)...'
        Push-Location $servidor
        & .\mvnw.cmd -q -DskipTests package
        Pop-Location
    }
    # chcp 65001 + UTF-8 en Java: los logs con tildes se ven bien en la consola.
    Start-Process cmd -WorkingDirectory $servidor `
        -ArgumentList "/k chcp 65001 >nul && title Servidor GraphQL - puerto 8081 && `"$java`" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -jar `"$jar`""
    if (Esperar 'El servidor' 90 { ServidorResponde }) { Bien "listo en $urlGraphQL" }
}

# --------------------------------------------------------------------------------------------
Paso 'Logs del servidor (en vivo)'

$visorLogs = Join-Path $PSScriptRoot 'ver-logs.ps1'
try {
    Start-Process powershell.exe -WorkingDirectory $servidor -ErrorAction Stop `
        -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$visorLogs`""
    Bien 'ventana Logs Libreria abierta (web y app)'
} catch {
    Aviso "no se pudo abrir el visor de logs: $($_.Exception.Message)"
}

# --------------------------------------------------------------------------------------------
Paso 'MongoDB Compass (ver las colecciones)'

$uriMongo = 'mongodb://localhost:27017'
$compass = @(
    (Join-Path $env:LOCALAPPDATA 'MongoDBCompass\MongoDBCompass.exe'),
    'C:\Program Files\MongoDB Compass\MongoDBCompass.exe'
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $compass) {
    Aviso 'MongoDB Compass no esta instalado (opcional): winget install MongoDB.Compass.Full'
} elseif (Get-Process MongoDBCompass -ErrorAction SilentlyContinue) {
    # Compass ignora la cadena de conexion si ya esta abierto: se revisa si de verdad esta conectado.
    $idsCompass = (Get-Process MongoDBCompass).Id
    $conectado = netstat -ano | Where-Object {
        $_ -match ':27017\s+ESTABLISHED\s+(\d+)\s*$' -and $idsCompass -contains [int]$Matches[1]
    }
    if ($conectado) {
        Bien 'Compass ya estaba abierto y conectado'
    } else {
        Aviso 'Compass esta abierto sin conexion: pulsa Connect en localhost:27017'
    }
} else {
    # Compass se conecta al servidor: ahi se ven las dos bases independientes.
    Start-Process -FilePath $compass -ArgumentList $uriMongo
    Bien "Compass abierto y conectado a $uriMongo"
}

# --------------------------------------------------------------------------------------------
Paso 'Bruno (coleccion de las APIs GraphQL y REST)'

$coleccion = (Join-Path $raiz 'bruno-libreria') -replace '\\', '/'
$bruno = @(
    (Join-Path $env:LOCALAPPDATA 'Programs\bruno\Bruno.exe'),
    'C:\Program Files\Bruno\Bruno.exe'
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $bruno) {
    Aviso 'Bruno no esta instalado (opcional): winget install Bruno.Bruno'
} else {
    # Agrega la coleccion al espacio de trabajo de Bruno una sola vez, sin tocar las demas.
    $espacio = Join-Path $env:APPDATA 'bruno\default-workspace\workspace.yml'
    if (Test-Path $espacio) {
        $texto = [IO.File]::ReadAllText($espacio)
        if ($texto -notmatch [regex]::Escape($coleccion)) {
            $entrada = "collections:`n  - name: `"Libreria - Microservicios`"`n    path: `"$coleccion`"`n"
            $nuevo = $texto -replace '(?m)^collections:[ \t]*\r?\n', $entrada.Replace('$', '$$')
            if ($nuevo -ne $texto) {
                [IO.File]::WriteAllText($espacio, $nuevo, (New-Object System.Text.UTF8Encoding($false)))
                Bien 'coleccion agregada a Bruno'
            } else {
                Aviso "abre la coleccion en Bruno con Open Collection: $coleccion"
            }
        } else {
            Bien 'la coleccion ya estaba en Bruno'
        }
    } else {
        Aviso "abre la coleccion en Bruno con Open Collection: $coleccion"
    }
    if (Get-Process Bruno -ErrorAction SilentlyContinue) {
        Bien 'Bruno ya estaba abierto'
    } else {
        Start-Process -FilePath $bruno
        Bien 'Bruno abierto (elige el entorno Demo)'
    }
}

# --------------------------------------------------------------------------------------------
Paso 'Cliente web (React, puerto 5173)'

if (Escuchando 5173) {
    Bien 'ya estaba corriendo'
} else {
    if (-not (Test-Path (Join-Path $web 'node_modules'))) {
        Aviso 'instalando dependencias de npm (solo la primera vez)...'
        Push-Location $web
        & npm install
        Pop-Location
    }
    Start-Process cmd -WorkingDirectory $web -ArgumentList '/k title Cliente web React - puerto 5173 && npm run dev'
    if (Esperar 'El cliente web' 60 { Escuchando 5173 }) { Bien "listo en $urlWeb" }
}
Start-Process $urlWeb

# --------------------------------------------------------------------------------------------
Paso 'Celular por USB (app Flutter)'

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    foreach ($c in @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", 'C:\Android\platform-tools\adb.exe')) {
        if (Test-Path $c) { $adb = $c; break }
    }
}

if (-not $adb) {
    Falla 'no se encontro adb (Android platform-tools)'
} else {
    if (Desactualizado $apk @((Join-Path $movil 'lib'), (Join-Path $movil 'pubspec.yaml'))) {
        Aviso 'compilando el APK con la ultima version (unos minutos)...'
        Push-Location $movil
        & flutter build apk --release --dart-define=URL_GRAPHQL=http://localhost:8081/graphql
        Pop-Location
    }
    & $adb start-server 2>$null | Out-Null

    $estadoAnterior = ''
    while ($true) {
        $lineas = & $adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() }
        if ($lineas | Where-Object { $_ -match '\tdevice$' }) { break }

        if ($lineas | Where-Object { $_ -match '\tunauthorized$' }) { $estado = 'sin-permiso' } else { $estado = 'sin-celular' }
        if ($estado -ne $estadoAnterior) {
            if ($estado -eq 'sin-permiso') {
                Aviso 'en el celular acepta "Permitir depuracion USB" (marca "Permitir siempre")'
            } else {
                Aviso 'esperando el celular: conectalo por USB con la depuracion USB activada'
                Aviso '(Ctrl+C para terminar sin el celular; el servidor y la web siguen corriendo)'
            }
            $estadoAnterior = $estado
        }
        Start-Sleep -Seconds 2
    }
    Bien 'celular detectado'

    # El celular llega al servidor del computador por el cable: localhost:8081 del celular -> 8081 del PC
    & $adb reverse tcp:8081 tcp:8081 | Out-Null
    Bien 'adb reverse tcp:8081 -> servidor del computador'

    $salida = & $adb install -r $apk 2>&1 | Out-String
    if ($salida -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Aviso 'habia otra version firmada distinta; se reinstala'
        & $adb uninstall $paquete | Out-Null
        $salida = & $adb install -r $apk 2>&1 | Out-String
    }
    if ($salida -match 'Success') { Bien 'app instalada/actualizada' } else { Falla "no se pudo instalar: $salida" }

    & $adb shell am force-stop $paquete | Out-Null
    & $adb shell am start -n "$paquete/.MainActivity" | Out-Null
    Bien 'app abierta en el celular'
}

# --------------------------------------------------------------------------------------------
Write-Host ''
Write-Host '======================================================================' -ForegroundColor Cyan
Write-Host ' Todo listo para la demostracion' -ForegroundColor Cyan
Write-Host "   Login:     usuario $usuarioLogin / contrasena $claveLogin"
Write-Host "   GraphQL:   $urlGraphQL   (consola: http://localhost:8081/graphiql)"
Write-Host '   REST:      http://localhost:8082/prestamos (lo invoca GraphQL, no los clientes)'
Write-Host "   Web:       $urlWeb"
Write-Host '   Celular:   app "Libreria" (si se desconecta el cable, ejecutar de nuevo)'
Write-Host '   MongoDB:   bases "libreria_evaluacion" (libros, ejemplares) y "libreria_prestamos" (prestamos)'
Write-Host '   Compass:   mongodb://localhost:27017'
Write-Host '   Bruno:     coleccion "Libreria - Microservicios", entorno Demo'
Write-Host '   Logs:      ventana "Logs Libreria - web y app" (actualizacion en vivo)'
Write-Host ' Para apagar: cerrar las ventanas "Servidor GraphQL", "Microservicio Prestamos" y "Cliente web React".'
Write-Host '======================================================================' -ForegroundColor Cyan
