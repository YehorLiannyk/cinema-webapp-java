package yehor.epam.services.impl;

import org.slf4j.Logger;
import yehor.epam.dao.UserDao;
import yehor.epam.dao.factories.DaoFactory;
import yehor.epam.dao.factories.DaoFactoryDeliver;
import yehor.epam.entities.User;
import yehor.epam.exceptions.AuthException;
import yehor.epam.exceptions.ServiceException;
import yehor.epam.services.UserService;
import yehor.epam.utilities.LoggerManager;
import yehor.epam.utilities.PassEncryptionManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service class for User
 */
public class UserServiceImpl implements UserService {
    private static final Logger logger = LoggerManager.getLogger(UserServiceImpl.class);
    private static final String CLASS_NAME = UserServiceImpl.class.getName();

    private Map<String, String> getSaltAndPassByLogin(String login) throws ServiceException {
        Map<String, String> saltAndPassByLogin = new LinkedHashMap<>();
        try (DaoFactory factory = DaoFactoryDeliver.getInstance().getFactory()) {
            logCreatingDaoFactory();
            final UserDao userDAO = factory.getUserDao();
            saltAndPassByLogin = userDAO.getSaltAndPassByLogin(login);
        } catch (Exception e) {
            throwServiceException("Couldn't add film", e);
        }
        return saltAndPassByLogin;
    }

    @Override
    public void authenticateUser(String login, String password) throws ServiceException, AuthException {
        final Map<String, String> saltAndPass = getSaltAndPassByLogin(login);
        final String saltValue = saltAndPass.get("salt");
        final String encryptedPass = saltAndPass.get("password");
        PassEncryptionManager passManager = new PassEncryptionManager();
        final boolean isVerified = passManager.verifyUserPassword(password, encryptedPass, saltValue);
        if (!isVerified) throw new AuthException("Wrong password");
    }

    @Override
    public User getUserByLogin(String login) throws ServiceException {
        User user = null;
        try (DaoFactory factory = DaoFactoryDeliver.getInstance().getFactory()) {
            logCreatingDaoFactory();
            final UserDao userDAO = factory.getUserDao();
            user = userDAO.getUserByLogin(login);
        } catch (Exception e) {
            throwServiceException("Couldn't get user by login", e);
        }
        return user;
    }

    @Override
    public User getById(int id) throws ServiceException {
        User user = null;
        try (DaoFactory factory = DaoFactoryDeliver.getInstance().getFactory()) {
            logCreatingDaoFactory();
            final UserDao userDAO = factory.getUserDao();
            user = userDAO.findById(id);
        } catch (Exception e) {
            throwServiceException("Couldn't find user with id: " + id, e);
        }
        return user;
    }

    @Override
    public int getMaxId() throws ServiceException {
        try (DaoFactory factory = DaoFactoryDeliver.getInstance().getFactory()) {
            logCreatingDaoFactory();
            final UserDao userDAO = factory.getUserDao();
            return userDAO.getMaxId();
        } catch (Exception e) {
            throwServiceException("Couldn't get max id", e);
        }
        return -1;
    }

    @Override
    public boolean save(User user) throws ServiceException {
        try (DaoFactory factory = DaoFactoryDeliver.getInstance().getFactory()) {
            logCreatingDaoFactory();
            final UserDao userDAO = factory.getUserDao();
            return userDAO.insert(user);
        } catch (Exception e) {
            throwServiceException("Couldn't save user", e);
        }
        return false;
    }


    private void logCreatingDaoFactory() {
        logger.debug("Created DAOFactory in " + CLASS_NAME);
    }

    private void throwServiceException(String message, Exception e) throws ServiceException {
        logger.error(message, e);
        throw new ServiceException(message, e);
    }
}
