package org.pf4j.demo.welcome;

import org.pf4j.Extension;
import org.pf4j.demo.api.Greeting;

@Extension
public class WelcomeGreeting implements Greeting {

    @Override
    public String getGreeting() {
        return "Welcome";
    }

}