/*
 * Copyright 2006 Niclas Hedhman.
 *
 * Licensed  under the  Apache License,  Version 2.0  (the "License");
 * you may not use  this file  except in  compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.runner.exec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.ParserConfigurationException;
import org.ops4j.pax.runner.PomInfo;
import org.ops4j.pax.runner.Runner;
import org.ops4j.pax.runner.ServiceException;
import org.ops4j.pax.runner.internal.RunnerOptions;
import org.ops4j.pax.runner.repositories.BundleRef;
import org.ops4j.pax.runner.state.Bundle;
import org.ops4j.pax.runner.utils.FileUtils;
import org.ops4j.pax.runner.utils.Pipe;
import org.xml.sax.SAXException;

public class EquinoxRunner extends AbstractRunner
    implements Runner
{

    private static final PomInfo[] DEFAULT_POMS =
        {
            new PomInfo( "org.eclipse.osgi", "services", "3.1.100.v20060601" ),
            new PomInfo( "org.eclipse.osgi", "util", "3.1.100.v20060601" )
        };

    private static final PomInfo[] SYSTEM_POMS =
        {
            new PomInfo( "org.eclipse", "osgi", "3.2.1.R32x_v20060717" )
        };

    private static final PomInfo[] GUI_POMS =
        {
        };

    private Properties m_props;

    public EquinoxRunner( Properties props )
        throws IOException, ParserConfigurationException, SAXException, ServiceException
    {
        m_props = props;
    }

    protected PomInfo[] getGuiBundles()
    {
        return GUI_POMS;
    }

    protected PomInfo[] getDefaultBundles()
    {
        return DEFAULT_POMS;
    }

    protected PomInfo[] getSystemBundles()
    {
        return SYSTEM_POMS;
    }

    protected void createConfigFile( RunnerOptions options, List<Bundle> bundles )
        throws IOException
    {
        File confDir = new File( options.getWorkDir(), "configuration" );
        confDir.mkdirs();
        File file = new File( confDir, "config.ini" );
        Writer out = FileUtils.openPropertyFile( file );
        try
        {
            boolean first = true;
            boolean clean = options.isRunClean();
            if( clean )
            {
                out.write( "\nosgi.clean=true\n" );
            }
            out.write( "\neclipse.ignoreApp=true\n" );
            out.write( "\nosgi.bundles=\\\n" );
            for( Bundle bundle : bundles )
            {
                if( !first )
                {
                    out.write( ",\\\n" );
                }
                first = false;
                String urlString = bundle.getBundleInfo().getReference().getLocation().toString();
                out.write( "reference:" );
                out.write( urlString );
                out.write( "@" + bundle.getStartLevel() );
                out.write( ":" );
                out.write( bundle.getTargetState().toString().toLowerCase() );
            }
            out.write( '\n' );
            out.write( '\n' );
            for( Map.Entry entry : m_props.entrySet() )
            {
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();
                out.write( key );
                out.write( "=" );
                out.write( value );
                out.write( "\n\n" );
            }
            out.flush();
        } finally
        {
            if( out != null )
            {
                out.close();
            }
        }
    }

    protected void runIt( RunnerOptions options, List<Bundle> systemBundles )
        throws IOException, InterruptedException
    {
        Runtime runtime = Runtime.getRuntime();

        File cwd = new File( System.getProperty( "user.dir" ) );
        String javaHome = System.getProperty( "java.home" );
        if( javaHome == null )
        {
            javaHome = System.getenv().get( "JAVA_HOME" );
        }
        if( javaHome == null )
        {
            System.err.println( "JAVA_HOME is not set." );
        }
        else
        {
            File workDir = options.getWorkDir();
            BundleRef ref = systemBundles.get(0).getBundleInfo().getReference();
            URL location = ref.getLocation();
            String jarFile = location.getPath();
            String[] cmd =
                {
                    javaHome + "/bin/java",
                    "-jar",
                    jarFile,
                    "-console",
                    "-configuration",
                    workDir.getAbsolutePath() + "/configuration",
                    "-install",
                    workDir.getAbsolutePath()
                };
            Process process = runtime.exec( cmd, null, cwd );
            InputStream err = process.getErrorStream();
            InputStream out = process.getInputStream();
            OutputStream in = process.getOutputStream();
            Pipe errPipe = new Pipe( err, System.err );
            errPipe.start();
            Pipe outPipe = new Pipe( out, System.out );
            outPipe.start();
            Pipe inPipe = new Pipe( System.in, in );
            inPipe.start();
            process.waitFor();
            inPipe.stop();
            outPipe.stop();
            errPipe.stop();
        }
    }
}
