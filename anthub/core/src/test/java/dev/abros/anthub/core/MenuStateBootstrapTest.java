package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MenuStateBootstrapTest {
 @Test void droppedInitialRequestIsRetriedUntilConfirmed(){var state=new MenuStateBootstrap();assertTrue(state.requestDue(100,true,true));assertFalse(state.requestDue(5099,true,true));assertTrue(state.requestDue(5100,true,true));state.confirm();assertFalse(state.requestDue(10100,true,true));}
 @Test void waitForPlayerAndChannelWithoutSpendingAttempt(){var state=new MenuStateBootstrap();assertFalse(state.requestDue(100,false,true));assertFalse(state.requestDue(100,true,false));assertTrue(state.requestDue(100,true,true));}
 @Test void unsolicitedStateDoesNotSkipHandshakeAndReconnectResetsIt(){var state=new MenuStateBootstrap();state.confirm();assertTrue(state.requestDue(100,true,true));state.confirm();state.reset();assertTrue(state.requestDue(101,true,true));}
}
