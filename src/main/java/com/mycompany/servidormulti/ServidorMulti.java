package com.mycompany.servidormulti;
import com.mycompany.servidormulti.BaseDatos;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;

public class ServidorMulti {
    static java.util.HashMap<String, UnCliente> clientes = new java.util.HashMap<>();
    static java.util.HashMap<String, String> usuarios = new java.util.HashMap<>();
    static java.util.HashMap<String, PartidaGato> partidasActivas = new java.util.HashMap<>();
    static java.util.HashMap<String, String> invitacionesPendientes = new java.util.HashMap<>();
    static final String DB_URL = "jdbc:sqlite:chat.db";
    static BaseDatos bd;
    
    public static void main(String[] args) {
        int puerto = 8080;
        
        bd = new BaseDatos();
        bd.inicializar();
        cargarUsuariosDelBD();
        
        try (ServerSocket servidorSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado en el puerto " + puerto);
            System.out.println("Sistema de autenticación activado: 3 mensajes gratuitos");
            System.out.println("Sistema de bloqueo activado");
            System.out.println("Sistema de juego Gato activado");
            System.out.println("Sistema de ranking activado");
            System.out.println("Sistema de grupos activado");
            System.out.println("Usuarios cargados: " + usuarios.size());
            
            while (true) {
                Socket socket = servidorSocket.accept();
                UnCliente unCliente = new UnCliente(socket);
                Thread hilo = new Thread(unCliente);
                hilo.start();
            }
        } catch (IOException e) {
            System.out.println("Error en el servidor: " + e.getMessage());
        }
    }
    
    public static synchronized boolean nombreDisponible(String nombre) {
        return !clientes.containsKey(nombre);
    }
    
    public static synchronized void registrarCliente(String nombre, UnCliente cliente) {
        clientes.put(nombre, cliente);
    }
    
    public static synchronized void registrarUsuario(String nombre, String password) {
        usuarios.put(nombre, password);
        bd.guardarUsuario(nombre, password);
        System.out.println("Nuevo usuario registrado: " + nombre);
    }
    
    public static synchronized boolean autenticarUsuario(String nombre, String password) {
        return usuarios.containsKey(nombre) && usuarios.get(nombre).equals(password);
    }
    
    public static boolean bloquearUsuario(String usuarioActual, String usuarioABloquear) {
        if (usuarioActual.equals(usuarioABloquear)) {
            return false;
        }
        if (!usuarios.containsKey(usuarioABloquear)) {
            return false;
        }
        return bd.bloquearUsuario(usuarioActual, usuarioABloquear);
    }
    
    public static boolean desbloquearUsuario(String usuarioActual, String usuarioADesbloquear) {
        if (!usuarios.containsKey(usuarioADesbloquear)) {
            return false; 
        }
        return bd.desbloquearUsuario(usuarioActual, usuarioADesbloquear);
    }
    
    public static boolean estasBloqueado(String usuarioOrigen, String usuarioDestino) {
        return bd.estaBloqueado(usuarioOrigen, usuarioDestino);
    }
    
    public static java.util.List<String> obtenerBloqueados(String usuario) {
        return bd.obtenerBloqueados(usuario);
    }
    
    private static void cargarUsuariosDelBD() {
        usuarios = bd.cargarTodosLosUsuarios();
        System.out.println("Usuarios cargados de la BD: " + usuarios.size());
    }
    
   
    public static boolean crearGrupo(String nombreGrupo, String creador) {
        return bd.crearGrupo(nombreGrupo, creador);
    }
    
    public static boolean eliminarGrupo(String nombreGrupo) {
        return bd.eliminarGrupo(nombreGrupo);
    }
    
    public static boolean unirseAGrupo(String usuario, String nombreGrupo) {
        return bd.unirseAGrupo(usuario, nombreGrupo);
    }
    
    public static boolean salirDeGrupo(String usuario, String nombreGrupo) {
        return bd.salirDeGrupo(usuario, nombreGrupo);
    }
    
    public static boolean existeGrupo(String nombreGrupo) {
        return bd.existeGrupo(nombreGrupo);
    }
    
    public static boolean esMiembroDeGrupo(String usuario, String nombreGrupo) {
        return bd.esMiembroDeGrupo(usuario, nombreGrupo);
    }
    
    public static java.util.List<String> obtenerGruposDisponibles() {
        return bd.obtenerGruposDisponibles();
    }
    
    public static java.util.List<String> obtenerMisGrupos(String usuario) {
        return bd.obtenerMisGrupos(usuario);
    }
    
    public static java.util.List<String> obtenerMiembrosGrupo(String nombreGrupo) {
        return bd.obtenerMiembrosGrupo(nombreGrupo);
    }
    
    public static long guardarMensajeGrupo(String nombreGrupo, String remitente, String mensaje) {
        return bd.guardarMensajeGrupo(nombreGrupo, remitente, mensaje);
    }
    
    public static void actualizarUltimoMensajeLeido(String usuario, String nombreGrupo, long idMensaje) {
        bd.actualizarUltimoMensajeLeido(usuario, nombreGrupo, idMensaje);
    }
    
    public static java.util.List<BaseDatos.MensajeGrupo> obtenerMensajesNoLeidos(String usuario, String nombreGrupo) {
        return bd.obtenerMensajesNoLeidos(usuario, nombreGrupo);
    }
    
    public static int contarMensajesNoLeidos(String usuario, String nombreGrupo) {
        return bd.contarMensajesNoLeidos(usuario, nombreGrupo);
    }
    
    public static synchronized boolean enviarInvitacionGato(String invitador, String invitado) {
        String claveInvitacion = invitado + "_invitacion";
        if (invitacionesPendientes.containsKey(claveInvitacion)) {
            return false; 
        }
        invitacionesPendientes.put(claveInvitacion, invitador);
        return true;
    }
    
    public static synchronized String obtenerInvitador(String invitado) {
        String claveInvitacion = invitado + "_invitacion";
        return invitacionesPendientes.get(claveInvitacion);
    }
    
    public static synchronized void eliminarInvitacion(String invitado) {
        String claveInvitacion = invitado + "_invitacion";
        invitacionesPendientes.remove(claveInvitacion);
    }
    
    public static synchronized boolean crearPartida(String jugador1, String jugador2) {
        String clavePartida1 = jugador1 + "_" + jugador2;
        String clavePartida2 = jugador2 + "_" + jugador1;
        
        if (partidasActivas.containsKey(clavePartida1) || partidasActivas.containsKey(clavePartida2)) {
            return false; 
        }
        
        boolean empiezaJ1 = Math.random() < 0.5;
        PartidaGato partida = new PartidaGato(jugador1, jugador2, empiezaJ1);
        
        partidasActivas.put(clavePartida1, partida);
        partidasActivas.put(clavePartida2, partida);
        
        System.out.println("Partida creada entre " + jugador1 + " y " + jugador2);
        return true;
    }
    
    public static synchronized PartidaGato obtenerPartida(String jugador1, String jugador2) {
        String clavePartida = jugador1 + "_" + jugador2;
        return partidasActivas.get(clavePartida);
    }
    
    public static synchronized void finalizarPartida(String jugador1, String jugador2) {
        String clavePartida1 = jugador1 + "_" + jugador2;
        String clavePartida2 = jugador2 + "_" + jugador1;
        
        partidasActivas.remove(clavePartida1);
        partidasActivas.remove(clavePartida2);
        
        System.out.println("Partida finalizada entre " + jugador1 + " y " + jugador2);
    }
    
    public static synchronized boolean tienePartidaActiva(String jugador) {
        for (PartidaGato partida : partidasActivas.values()) {
            if (partida.esJugadorEnPartida(jugador) && !partida.isTerminado()) {
                return true;
            }
        }
        return false;
    }
    
    public static synchronized java.util.List<PartidaGato> obtenerPartidasDeJugador(String jugador) {
        java.util.List<PartidaGato> partidas = new java.util.ArrayList<>();
        java.util.Set<PartidaGato> partidasVistas = new java.util.HashSet<>();
        
        for (PartidaGato partida : partidasActivas.values()) {
            if (partida.esJugadorEnPartida(jugador) && !partidasVistas.contains(partida)) {
                partidas.add(partida);
                partidasVistas.add(partida);
            }
        }
        return partidas;
    }
  
    public static void registrarResultadoPartida(String jugador1, String jugador2, String ganador) {
        bd.registrarResultadoPartida(jugador1, jugador2, ganador);
    }
    
    public static java.util.List<String> obtenerRankingGeneral() {
        return bd.obtenerRankingGeneral();
    }
    
    public static BaseDatos.EstadisticasEnfrentamiento obtenerEstadisticasEnfrentamiento(String jugador1, String jugador2) {
        return bd.obtenerEstadisticasEnfrentamiento(jugador1, jugador2);
    }
}