package esvar.ua.pi_control_panel;

import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;

@SpringBootApplication
@StyleSheet("styles.css")
@Push(PushMode.AUTOMATIC)
public class PiControlPanelApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(PiControlPanelApplication.class, args);
    }
}
