package tech.kayys.aljabr.engine;
import java.net.URL;
import java.io.File;
import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
public class ListClasses {
    public static void main(String[] args) throws Exception {
        String cp = System.getProperty("java.class.path");
        for (String p : cp.split(":")) {
            if (p.endsWith(".jar") && p.contains("andalus")) {
                ZipFile zip = new ZipFile(p);
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().contains("InferenceTypes")) {
                        System.out.println(entry.getName());
                    }
                }
            }
        }
    }
}
