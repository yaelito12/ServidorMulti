package com.mycompany.servidormulti;

import java.sql.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class BaseDatos {
    private static final String DB_URL = "jdbc:sqlite:chat.db";
    
    public void inicializar() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            // Tabla de usuarios
            stmt.execute("CREATE TABLE IF NOT EXISTS usuarios (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "nombre TEXT UNIQUE NOT NULL," +
                    "password TEXT NOT NULL," +
                    "fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
            // Tabla de bloqueos
            stmt.execute("CREATE TABLE IF NOT EXISTS bloqueados (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "usuario_que_bloquea TEXT NOT NULL," +
                    "usuario_bloqueado TEXT NOT NULL," +
                    "fecha_bloqueo TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(usuario_que_bloquea, usuario_bloqueado)," +
                    "FOREIGN KEY(usuario_que_bloquea) REFERENCES usuarios(nombre)," +
                    "FOREIGN KEY(usuario_bloqueado) REFERENCES usuarios(nombre))");
            
            // Tabla de estadísticas de juego
            stmt.execute("CREATE TABLE IF NOT EXISTS estadisticas_gato (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "jugador TEXT NOT NULL," +
                    "victorias INTEGER DEFAULT 0," +
                    "empates INTEGER DEFAULT 0," +
                    "derrotas INTEGER DEFAULT 0," +
                    "puntos INTEGER DEFAULT 0," +
                    "UNIQUE(jugador)," +
                    "FOREIGN KEY(jugador) REFERENCES usuarios(nombre))");
            
            // Tabla de historial de partidas entre jugadores
            stmt.execute("CREATE TABLE IF NOT EXISTS historial_partidas (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "jugador1 TEXT NOT NULL," +
                    "jugador2 TEXT NOT NULL," +
                    "ganador TEXT," +
                    "fecha_partida TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY(jugador1) REFERENCES usuarios(nombre)," +
                    "FOREIGN KEY(jugador2) REFERENCES usuarios(nombre))");
            
            // Tabla de grupos
            stmt.execute("CREATE TABLE IF NOT EXISTS grupos (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "nombre TEXT UNIQUE NOT NULL," +
                    "creador TEXT NOT NULL," +
                    "fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "es_sistema INTEGER DEFAULT 0," +
                    "FOREIGN KEY(creador) REFERENCES usuarios(nombre))");
            
           
            System.out.println("Base de datos inicializada correctamente");
        } catch (SQLException e) {
            System.err.println("Error inicializando BD: " + e.getMessage());
        }
    }
    
    public synchronized void guardarUsuario(String nombre, String password) {
        String sqlUsuario = "INSERT INTO usuarios (nombre, password) VALUES (?, ?)";
        String sqlEstadisticas = "INSERT OR IGNORE INTO estadisticas_gato (jugador, victorias, empates, derrotas, puntos) VALUES (?, 0, 0, 0, 0)";
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            
            try {
                // Insertar usuario
                try (PreparedStatement pstmt = conn.prepareStatement(sqlUsuario)) {
                    pstmt.setString(1, nombre);
                    pstmt.setString(2, password);
                    pstmt.executeUpdate();
                }
                
                // Inicializar estadísticas
                try (PreparedStatement pstmt = conn.prepareStatement(sqlEstadisticas)) {
                    pstmt.setString(1, nombre);
                    pstmt.executeUpdate();
                }
                
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Error guardando usuario: " + e.getMessage());
        }
    }
    
    private void inicializarEstadisticasLote(Connection conn, String jugador) throws SQLException {
        String sql = "INSERT OR IGNORE INTO estadisticas_gato (jugador, victorias, empates, derrotas, puntos) VALUES (?, 0, 0, 0, 0)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, jugador);
            pstmt.executeUpdate();
        }
    }
    
    public HashMap<String, String> cargarTodosLosUsuarios() {
        HashMap<String, String> usuarios = new HashMap<>();
        String sqlSelect = "SELECT nombre, password FROM usuarios";
        
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // Primero cargar todos los usuarios
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sqlSelect)) {
                
                while (rs.next()) {
                    String nombre = rs.getString("nombre");
                    usuarios.put(nombre, rs.getString("password"));
                }
            }
            
            // Luego inicializar estadísticas para todos en una sola transacción
            conn.setAutoCommit(false);
            try {
                for (String nombre : usuarios.keySet()) {
                    inicializarEstadisticasLote(conn, nombre);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            
        } catch (SQLException e) {
            System.err.println("Error cargando usuarios: " + e.getMessage());
        }
        return usuarios;
    }
    
    public synchronized void registrarResultadoPartida(String jugador1, String jugador2, String ganador) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            
            try {
                // Registrar en historial
                String sqlHistorial = "INSERT INTO historial_partidas (jugador1, jugador2, ganador) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlHistorial)) {
                    pstmt.setString(1, jugador1);
                    pstmt.setString(2, jugador2);
                    pstmt.setString(3, ganador);
                    pstmt.executeUpdate();
                }
                
                // Actualizar estadísticas
                if ("EMPATE".equals(ganador)) {
                    actualizarEstadistica(conn, jugador1, 0, 1, 0);
                    actualizarEstadistica(conn, jugador2, 0, 1, 0);
                } else {
                    String perdedor = ganador.equals(jugador1) ? jugador2 : jugador1;
                    actualizarEstadistica(conn, ganador, 1, 0, 0);
                    actualizarEstadistica(conn, perdedor, 0, 0, 1);
                }
                
                conn.commit();
                System.out.println("Resultado registrado: " + jugador1 + " vs " + jugador2 + " - Ganador: " + ganador);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Error registrando resultado: " + e.getMessage());
        }
    }
    
    private void actualizarEstadistica(Connection conn, String jugador, int victorias, int empates, int derrotas) throws SQLException {
        int puntos = (victorias * 2) + empates;
        String sql = "UPDATE estadisticas_gato SET victorias = victorias + ?, empates = empates + ?, " +
                     "derrotas = derrotas + ?, puntos = puntos + ? WHERE jugador = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, victorias);
            pstmt.setInt(2, empates);
            pstmt.setInt(3, derrotas);
            pstmt.setInt(4, puntos);
            pstmt.setString(5, jugador);
            pstmt.executeUpdate();
        }
    }
    
    public List<String> obtenerRankingGeneral() {
        List<String> ranking = new ArrayList<>();
        String sql = "SELECT jugador, victorias, empates, derrotas, puntos, " +
                     "(victorias + empates + derrotas) as partidas_totales " +
                     "FROM estadisticas_gato WHERE partidas_totales > 0 " +
                     "ORDER BY puntos DESC, victorias DESC, jugador ASC";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            int posicion = 1;
            while (rs.next()) {
                String jugador = rs.getString("jugador");
                int victorias = rs.getInt("victorias");
                int empates = rs.getInt("empates");
                int derrotas = rs.getInt("derrotas");
                int puntos = rs.getInt("puntos");
                int partidas = rs.getInt("partidas_totales");
                
                String linea = String.format("%d. %s - %d pts (%dV/%dE/%dD) - %d partidas",
                        posicion, jugador, puntos, victorias, empates, derrotas, partidas);
                ranking.add(linea);
                posicion++;
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo ranking: " + e.getMessage());
        }
        
        return ranking;
    }
    
    public EstadisticasEnfrentamiento obtenerEstadisticasEnfrentamiento(String jugador1, String jugador2) {
        EstadisticasEnfrentamiento stats = new EstadisticasEnfrentamiento();
        stats.jugador1 = jugador1;
        stats.jugador2 = jugador2;
        
        String sql = "SELECT ganador FROM historial_partidas " +
                     "WHERE (jugador1 = ? AND jugador2 = ?) OR (jugador1 = ? AND jugador2 = ?)";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, jugador1);
            pstmt.setString(2, jugador2);
            pstmt.setString(3, jugador2);
            pstmt.setString(4, jugador1);
            
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                String ganador = rs.getString("ganador");
                if ("EMPATE".equals(ganador)) {
                    stats.empates++;
                } else if (jugador1.equals(ganador)) {
                    stats.victoriasJ1++;
                } else if (jugador2.equals(ganador)) {
                    stats.victoriasJ2++;
                }
            }
            
            stats.calcularPorcentajes();
        } catch (SQLException e) {
            System.err.println("Error obteniendo estadísticas de enfrentamiento: " + e.getMessage());
        }
        
        return stats;
    }
    
    public synchronized boolean bloquearUsuario(String usuarioActual, String usuarioABloquear) {
        if (estaBloqueado(usuarioActual, usuarioABloquear)) {
            return false;
        }
        
        String sql = "INSERT INTO bloqueados (usuario_que_bloquea, usuario_bloqueado) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuarioActual);
            pstmt.setString(2, usuarioABloquear);
            pstmt.executeUpdate();
            System.out.println(usuarioActual + " bloqueó a " + usuarioABloquear);
            return true;
        } catch (SQLException e) {
            System.err.println("Error bloqueando usuario: " + e.getMessage());
            return false;
        }
    }
    
    public synchronized boolean desbloquearUsuario(String usuarioActual, String usuarioADesbloquear) {
        if (!estaBloqueado(usuarioActual, usuarioADesbloquear)) {
            return false;
        }
        
        String sql = "DELETE FROM bloqueados WHERE usuario_que_bloquea = ? AND usuario_bloqueado = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuarioActual);
            pstmt.setString(2, usuarioADesbloquear);
            pstmt.executeUpdate();
            System.out.println(usuarioActual + " desbloqueó a " + usuarioADesbloquear);
            return true;
        } catch (SQLException e) {
            System.err.println("Error desbloqueando usuario: " + e.getMessage());
            return false;
        }
    }
    
    public boolean estaBloqueado(String usuarioOrigen, String usuarioDestino) {
        String sql = "SELECT COUNT(*) FROM bloqueados WHERE usuario_que_bloquea = ? AND usuario_bloqueado = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuarioOrigen);
            pstmt.setString(2, usuarioDestino);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error verificando bloqueo: " + e.getMessage());
        }
        return false;
    }
    
    public List<String> obtenerBloqueados(String usuario) {
        List<String> bloqueados = new ArrayList<>();
        String sql = "SELECT usuario_bloqueado FROM bloqueados WHERE usuario_que_bloquea = ? ORDER BY fecha_bloqueo DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                bloqueados.add(rs.getString("usuario_bloqueado"));
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo bloqueados: " + e.getMessage());
        }
        return bloqueados;
    }
    
    public static class EstadisticasEnfrentamiento {
        public String jugador1;
        public String jugador2;
        public int victoriasJ1;
        public int victoriasJ2;
        public int empates;
        public double porcentajeJ1;
        public double porcentajeJ2;
        public int totalPartidas;
        
        public void calcularPorcentajes() {
            totalPartidas = victoriasJ1 + victoriasJ2 + empates;
            if (totalPartidas > 0) {
                porcentajeJ1 = (victoriasJ1 * 100.0) / totalPartidas;
                porcentajeJ2 = (victoriasJ2 * 100.0) / totalPartidas;
            } else {
                porcentajeJ1 = 0;
                porcentajeJ2 = 0;
            }
        }
    }
}