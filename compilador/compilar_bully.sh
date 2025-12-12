#!/bin/bash
# Script de compilación para Sistema de Subasta con Protocolo Bully
# Linux/Mac Shell Script

echo "========================================"
echo "   COMPILANDO SISTEMA BULLY"
echo "========================================"
echo

# Cambiar al directorio del script
cd "$(dirname "$0")"

echo "[1/7] Compilando MiSocketStream.java..."
javac -d . servidor/MiSocketStream.java || exit 1

echo "[2/7] Compilando MensajeBully.java..."
javac -d . servidor/MensajeBully.java || exit 1

echo "[3/7] Compilando NodoSubasta.java..."
javac -d . servidor/NodoSubasta.java || exit 1

echo "[4/7] Compilando GestorEleccion.java..."
javac -d . servidor/GestorEleccion.java || exit 1

echo "[5/7] Compilando HiloClienteSubastaBully.java..."
javac -d . servidor/HiloClienteSubastaBully.java || exit 1

echo "[6/7] Compilando ServidorSubastaBully.java..."
javac -d . servidor/ServidorSubastaBully.java || exit 1

echo "[7/7] Compilando clientes..."
javac -d . cliente/ClienteSubastaAuxiliar.java || exit 1
javac -d . cliente/ClienteSubasta.java || exit 1

echo
echo "========================================"
echo "   COMPILACION EXITOSA!"
echo "========================================"
echo
echo "Para ejecutar los servidores, abre 3 terminales y ejecuta:"
echo
echo "Terminal 1: java socket.conconexion.servidor.ServidorSubastaBully 1 8080 9080 nodos.conf"
echo "Terminal 2: java socket.conconexion.servidor.ServidorSubastaBully 2 8081 9081 nodos.conf"
echo "Terminal 3: java socket.conconexion.servidor.ServidorSubastaBully 3 8082 9082 nodos.conf"
echo
echo "Para conectar clientes: java socket.conconexion.cliente.ClienteSubasta"
echo
