package tech.kayys.aljabr.engine;
import tech.kayys.andalus.identity.ResourceId;
import java.lang.reflect.Method;
public class TestResourceId {
    public static void main(String[] args) {
        for (Method m : ResourceId.class.getMethods()) {
            System.out.println(m.getName() + " " + m.getReturnType().getName());
        }
    }
}
