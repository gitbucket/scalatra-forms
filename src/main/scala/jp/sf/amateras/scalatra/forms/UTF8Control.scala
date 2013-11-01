package jp.sf.amateras.scalatra.forms

import java.io.{InputStream, InputStreamReader}
import java.util.{Locale, ResourceBundle, PropertyResourceBundle}

class UTF8Control extends ResourceBundle.Control {
  
    override def newBundle(baseName: String, locale: Locale, format: String, loader: ClassLoader, reload: Boolean): ResourceBundle = {
        val bundleName   = toBundleName(baseName, locale);
        val resourceName = toResourceName(bundleName, "properties");
        var bundle: ResourceBundle = null;
        var stream: InputStream = null;
        if (reload) {
            val url = loader.getResource(resourceName);
            if (url != null) {
                val connection = url.openConnection();
                if (connection != null) {
                    connection.setUseCaches(false);
                    stream = connection.getInputStream();
                }
            }
        } else {
            stream = loader.getResourceAsStream(resourceName);
        }
        if (stream != null) {
            try {
                // Only this line is changed to make it to read properties files as UTF-8.
                bundle = new PropertyResourceBundle(new InputStreamReader(stream, "UTF-8"));
            } finally {
                stream.close();
            }
        }
        return bundle;
    }
    
}