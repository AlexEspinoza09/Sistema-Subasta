@echo off
REM Script de compilación para Sistema de Subasta con Protocolo Bully
REM Windows Batch Script

echo ========================================
echo   COMPILANDO SISTEMA BULLY
echo ========================================
echo.

REM Cambiar al directorio del proyecto
cd /d "%~dp0"

echo [1/7] Compilando MiSocketStream.java...
javac -encoding UTF-8 -d . servidor\MiSocketStream.java
if %errorlevel% neq 0 goto error

echo [2/7] Compilando MensajeBully.java...
javac -encoding UTF-8 -d . servidor\MensajeBully.java
if %errorlevel% neq 0 goto error

echo [3/7] Compilando NodoSubasta.java...
javac -encoding UTF-8 -d . servidor\NodoSubasta.java
if %errorlevel% neq 0 goto error

echo [4/7] Compilando GestorEleccion.java...
javac -encoding UTF-8 -d . servidor\GestorEleccion.java
if %errorlevel% neq 0 goto error

echo [5/7] Compilando HiloClienteSubastaBully.java...
javac -encoding UTF-8 -d . servidor\HiloClienteSubastaBully.java
if %errorlevel% neq 0 goto error

echo [6/7] Compilando ServidorSubastaBully.java...
javac -encoding UTF-8 -d . servidor\ServidorSubastaBully.java
if %errorlevel% neq 0 goto error

echo [7/7] Compilando clientes...
javac -encoding UTF-8 -d . cliente\ClienteSubastaAuxiliar.java
if %errorlevel% neq 0 goto error
javac -encoding UTF-8 -d . cliente\ClienteSubasta.java
if %errorlevel% neq 0 goto error

echo.
echo ========================================
echo   COMPILACION EXITOSA!
echo ========================================
echo.
echo Para ejecutar los servidores, abre 3 terminales y ejecuta:
echo.
echo Terminal 1: java socket.conconexion.servidor.ServidorSubastaBully 1 8080 9080 nodos.conf
echo Terminal 2: java socket.conconexion.servidor.ServidorSubastaBully 2 8081 9081 nodos.conf
echo Terminal 3: java socket.conconexion.servidor.ServidorSubastaBully 3 8082 9082 nodos.conf
echo.
echo Para conectar clientes: java socket.conconexion.cliente.ClienteSubasta
echo.
goto end

:error
echo.
echo ========================================
echo   ERROR EN LA COMPILACION
echo ========================================
echo.
echo Revisa los errores mostrados arriba.
pause
exit /b 1

:end
pause
