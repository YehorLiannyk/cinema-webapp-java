package yehor.epam.dao.mysql;

import org.apache.log4j.Logger;
import yehor.epam.dao.BaseDAO;
import yehor.epam.dao.SessionDAO;
import yehor.epam.dao.exception.DAOException;
import yehor.epam.entities.Film;
import yehor.epam.entities.Session;
import yehor.epam.utilities.LoggerManager;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static yehor.epam.utilities.OtherConstants.*;

public class MySQLSessionDAO extends BaseDAO implements SessionDAO {
    private static final Logger logger = LoggerManager.getLogger(MySQLSessionDAO.class);
    private static final String INSERT = "INSERT INTO sessions VALUES (session_id, ?,?,?,?,?)";
    private static final String DECREMENT_FREE_SEATS = "UPDATE sessions SET free_seats = free_seats - 1 WHERE session_id=? AND free_seats > 0";
    private static final String SELECT_ALL = "SELECT * FROM sessions s JOIN films f on s.film_id = f.film_id";
    private static final String SELECT_BY_ID = "SELECT * FROM sessions s JOIN films f on s.film_id = f.film_id WHERE s.session_id=?";
    private static final String SELECT_FREE_SEATS_BY_ID = "SELECT free_seats FROM sessions WHERE session_id=?";
    private static final String DELETE_BY_SESSION_ID = "DELETE FROM sessions WHERE session_id=?";

    private static final String WHERE_DEFAULT = " WHERE s.date>=? AND IF (s.date=?, s.time>=?, s.time>=?)";
    private static final String ORDER_BY_DATETIME_ASC = " ORDER BY s.date ASC, s.time ASC";
    private static final String ORDER_BY_DATETIME_DESC = " ORDER BY s.date DESC, s.time DESC";
    private static final String ORDER_BY_FILM_NAME = " ORDER BY f.film_name ";
    private static final String ORDER_BY_FREE_SEATS = " ORDER BY s.free_seats";
    private static final String DESCENDING = " DESC";


    @Override
    public boolean insert(Session session) {
        boolean inserted = false;
        try (PreparedStatement statement = getConnection().prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            setSessionToInsertStatement(session, statement);
            statement.executeUpdate();
            final MySQLSeatDAO seatDAO = getSeatDAO();
            final int sessionId = getLastGeneratedKey(statement);
            session.setId(sessionId);
            seatDAO.insertFreeSeatsForSession(session);
            inserted = true;
        } catch (SQLException e) {
            logger.error("Couldn't insert Session to Database", e);
            throw new DAOException("Couldn't insert Session to Database");
        }
        return inserted;
    }

    private int getLastGeneratedKey(PreparedStatement statement) throws SQLException {
        int key = -1;
        final ResultSet generatedKeys = statement.getGeneratedKeys();
        while (generatedKeys.next())
            key = generatedKeys.getInt(1);
        return key;
    }

    private void setSessionToInsertStatement(Session session, PreparedStatement statement) throws SQLException {
        final MySQLSeatDAO seatDAO = getSeatDAO();
        final int allSeatsAmount = seatDAO.getAllSeatsAmount();
        try {
            statement.setInt(1, session.getFilm().getId());
            final LocalDate date = session.getDate();
            final LocalTime time = session.getTime();
            statement.setDate(2, Date.valueOf(date));
            statement.setTime(3, Time.valueOf(time));
            statement.setBigDecimal(4, session.getTicketPrice());
            statement.setInt(5, allSeatsAmount);

        } catch (SQLException e) {
            logger.error("Couldn't set session to Statement", e);
            throw new SQLException("Couldn't set session to Statement", e);
        }
    }

    @Override
    public Session findById(int id) {
        Session session = null;
        try (PreparedStatement statement = getConnection().prepareStatement(SELECT_BY_ID)) {
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                session = getSessionFromResultSet(resultSet);
            }
        } catch (SQLException e) {
            logger.error("Couldn't find session by id in Database", e);
            throw new DAOException("Couldn't find session by id in Database");
        }
        return session;
    }

    @Override
    public List<Session> findAll() {
        final String request = SELECT_ALL + WHERE_DEFAULT + ORDER_BY_DATETIME_ASC;
        return getPreparedSessionListByRequest(request);
    }

    private List<Session> getPreparedSessionListByRequest(String request) {
        List<Session> sessionList = new ArrayList<>();
        try (PreparedStatement statement = getConnection().prepareStatement(request)) {
            final LocalDate nowDate = LocalDate.now();
            final LocalTime nowTime = LocalTime.now();
            statement.setDate(1, Date.valueOf(nowDate));
            statement.setDate(2, Date.valueOf(nowDate));
            statement.setTime(3, Time.valueOf(nowTime));
            statement.setTime(4, Time.valueOf(MIN_SESSION_TIME));
            logger.debug("Select: " + statement.toString());
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                final Session session = getSessionFromResultSet(resultSet);
                sessionList.add(session);
            }
        } catch (SQLException e) {
            logger.error("Couldn't get list of all sessionList from Database", e);
            throw new DAOException("Couldn't get list of all sessionList from Database");
        }
        return sessionList;
    }

    @Override
    public Session update(Session element) {
        return null;
    }

    @Override
    public boolean delete(Session element) {
        return delete(element.getId());
    }

    private Session getSessionFromResultSet(ResultSet rs) {
        Session session = null;
        try {
            session = new Session(
                    rs.getInt("session_id"),
                    rs.getBigDecimal("ticket_price"),
                    rs.getDate("date").toLocalDate(),
                    rs.getTime("time").toLocalTime(),
                    rs.getInt("free_seats")
            );
            final int filmId = rs.getInt("film_id");
            final Film film = getFilmDAO().findById(filmId);
            session.setFilm(film);
        } catch (SQLException e) {
            logger.error("Couldn't get session from ResultSet", e);
            throw new DAOException("Couldn't get session from ResultSet");
        }
        return session;
    }

    private MySQLFilmDAO getFilmDAO() {
        final MySQLFilmDAO mySQLFilmDAO = new MySQLFilmDAO();
        mySQLFilmDAO.setConnection(getConnection());
        return mySQLFilmDAO;
    }

    @Override
    public List<Session> getFilteredAndSortedSessionList(Map<String, String> map) {
        final String request = sortByFormRequest(map, SELECT_ALL + WHERE_DEFAULT);
        List<Session> sessionList = getPreparedSessionListByRequest(request);
        removeFromListUnavailableSessions(map, sessionList);
        return sessionList;
    }

    @Override
    public int getFreeSeatAmount(Session session) {
        return getFreeSeatAmount(session.getId());
    }

    @Override
    public int getFreeSeatAmount(int sessionId) {
        int amount = 0;
        try (PreparedStatement statement = getConnection().prepareStatement(SELECT_FREE_SEATS_BY_ID)) {
            statement.setInt(1, sessionId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                amount = resultSet.getInt("free_seats");
            }
        } catch (SQLException e) {
            logger.error("Couldn't get free seats", e);
            throw new DAOException("Couldn't get free seats");
        }
        return amount;
    }

    @Override
    public boolean delete(int sessionId) {
        boolean isDeleted = false;
        try (PreparedStatement statement = getConnection().prepareStatement(DELETE_BY_SESSION_ID)) {
            statement.setInt(1, sessionId);
            final int row = statement.executeUpdate();
            if (row > 1) throw new DAOException("Statement removed more than one row");
            isDeleted = true;
        } catch (SQLException e) {
            logger.error("Couldn't delete session", e);
            throw new DAOException("Couldn't delete session", e);
        }
        return isDeleted;
    }

    @Override
    public boolean decrementFreeSeatsAmount(int sessionId) {
        boolean isDecremented = true;
        try (PreparedStatement statement = getConnection().prepareStatement(DECREMENT_FREE_SEATS)) {
            statement.setInt(1, sessionId);
            final int row = statement.executeUpdate();
            if (row == 0) {
                logger.warn("Couldn't decrement session: " + sessionId + ", changed rows = " + row);
                isDecremented = false;
            }
        } catch (SQLException e) {
            logger.error("Couldn't decrement free seats amount of Session", e);
            throw new DAOException("Couldn't decrement free seats amount of Session");
        }
        return isDecremented;
    }

    private String sortByFormRequest(Map<String, String> map, String defaultRequest) {
        StringBuilder orderedRequest = new StringBuilder(defaultRequest);
        if (map.containsValue(SESSION_SORT_BY_DATETIME)) {
            if (map.containsValue(SESSION_SORT_METHOD_DESC)) {
                orderedRequest.append(ORDER_BY_DATETIME_DESC);
            } else {
                orderedRequest.append(ORDER_BY_DATETIME_ASC);
            }
        } else if (map.containsValue(SESSION_SORT_BY_FILM_NAME)) {
            orderedRequest.append(ORDER_BY_FILM_NAME);
            if (map.containsValue(SESSION_SORT_METHOD_DESC)) {
                orderedRequest.append(DESCENDING);
            }
        } else if (map.containsValue(SESSION_SORT_BY_SEATS_REMAIN)) {
            orderedRequest.append(ORDER_BY_FREE_SEATS);
            if (map.containsValue(SESSION_SORT_METHOD_DESC)) {
                orderedRequest.append(DESCENDING);
            }
        }
        return orderedRequest.toString();
    }

    private List<Session> removeFromListUnavailableSessions(Map<String, String> map, List<Session> sessionList) {
        if (map.containsValue(SESSION_FILTER_SHOW_ONLY_AVAILABLE)) {
            logger.debug("Map contains: " + SESSION_FILTER_SHOW_ONLY_AVAILABLE);
            final MySQLSeatDAO seatDAO = getSeatDAO();
            for (Session session : sessionList) {
                logger.debug("seatDAO.getFreeSeatsAmountBySessionId(session: " + session.getId() + ") > 0): " + seatDAO.getFreeSeatsAmountBySessionId(session.getId()));
                if (seatDAO.getFreeSeatsAmountBySessionId(session.getId()) == 0) {
                    sessionList.remove(session);
                }
            }
        }
        return sessionList;
    }


    private MySQLSeatDAO getSeatDAO() {
        final MySQLSeatDAO mySQLSeatDAO = new MySQLSeatDAO();
        mySQLSeatDAO.setConnection(getConnection());
        return mySQLSeatDAO;
    }
}
