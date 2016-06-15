package fi.helsinki.cs.tmc.cli.command;

import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;
import static org.mockito.Mockito.times;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import fi.helsinki.cs.tmc.cli.Application;
import fi.helsinki.cs.tmc.cli.io.TestIo;
import fi.helsinki.cs.tmc.cli.tmcstuff.SettingsIo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SettingsIo.class)
public class LogoutCommandTest {

    Application app;
    TestIo io;

    @Before
    public void setUp() {
        io = new TestIo();
        app = new Application(io);

        PowerMockito.mockStatic(SettingsIo.class);
    }

    @Test
    public void hello() {
        String[] args = {"logout"};
        app.run(args);

        verifyStatic(times(1));
        SettingsIo.delete();

        assertThat(io.out(), containsString("Logged out."));
    }
}