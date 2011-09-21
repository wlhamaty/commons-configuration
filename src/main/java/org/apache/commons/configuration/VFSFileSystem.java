/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.configuration;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.UriParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.util.Map;
import java.util.Iterator;
import java.lang.reflect.Method;

/**
 * FileSystem that uses Commons VFS
 * @since 1.7
 * @author <a
 * href="http://commons.apache.org/configuration/team-list.html">Commons Configuration team</a>
 */
public class VFSFileSystem extends DefaultFileSystem
{
    public VFSFileSystem()
    {
    }

    public InputStream getInputStream(String basePath, String fileName)
        throws ConfigurationException
    {
        try
        {
            FileSystemManager manager = VFS.getManager();
            FileName path;
            if (basePath != null)
            {
                FileName base = manager.resolveURI(basePath);
                path = manager.resolveName(base, fileName);
            }
            else
            {
                FileName file = manager.resolveURI(fileName);
                FileName base = file.getParent();
                path = manager.resolveName(base, file.getBaseName());
            }
            FileSystemOptions opts = getOptions(path.getScheme());
            FileObject file = (opts == null) ? manager.resolveFile(path.getURI())
                    : manager.resolveFile(path.getURI(), opts);
            FileContent content = file.getContent();
            if (content == null)
            {
                String msg = "Cannot access content of " + file.getName().getFriendlyURI();
                throw new ConfigurationException(msg);
            }
            return content.getInputStream();
        }
        catch (ConfigurationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ConfigurationException("Unable to load the configuration file " + fileName, e);
        }
    }

    public InputStream getInputStream(URL url) throws ConfigurationException
    {
        FileObject file;
        try
        {
            FileSystemOptions opts = getOptions(url.getProtocol());
            file = (opts == null) ? VFS.getManager().resolveFile(url.toString())
                    : VFS.getManager().resolveFile(url.toString(), opts);
            if (file.getType() != FileType.FILE)
            {
                throw new ConfigurationException("Cannot load a configuration from a directory");
            }
            FileContent content = file.getContent();
            if (content == null)
            {
                String msg = "Cannot access content of " + file.getName().getFriendlyURI();
                throw new ConfigurationException(msg);
            }
            return content.getInputStream();
        }
        catch (FileSystemException fse)
        {
            String msg = "Unable to access " + url.toString();
            throw new ConfigurationException(msg, fse);
        }
    }

    public OutputStream getOutputStream(URL url) throws ConfigurationException
    {
        try
        {
            FileSystemOptions opts = getOptions(url.getProtocol());
            FileSystemManager fsManager = VFS.getManager();
            FileObject file = (opts == null) ? fsManager.resolveFile(url.toString())
                    : fsManager.resolveFile(url.toString(), opts);
            // throw an exception if the target URL is a directory
            if (file == null || file.getType() == FileType.FOLDER)
            {
                throw new ConfigurationException("Cannot save a configuration to a directory");
            }
            FileContent content = file.getContent();

            if (content == null)
            {
                throw new ConfigurationException("Cannot access content of " + url);
            }
            return content.getOutputStream();
        }
        catch (FileSystemException fse)
        {
            throw new ConfigurationException("Unable to access " + url, fse);
        }
    }

    public String getPath(File file, URL url, String basePath, String fileName)
    {
        if (file != null)
        {
            return super.getPath(file, url, basePath, fileName);
        }
        try
        {
            FileSystemManager fsManager = VFS.getManager();
            if (url != null)
            {
                FileName name = fsManager.resolveURI(url.toString());
                if (name != null)
                {
                    return name.toString();
                }
            }

            if (UriParser.extractScheme(fileName) != null)
            {
                return fileName;
            }
            else if (basePath != null)
            {
                FileName base = fsManager.resolveURI(basePath);
                return fsManager.resolveName(base, fileName).getURI();
            }
            else
            {
                FileName name = fsManager.resolveURI(fileName);
                FileName base = name.getParent();
                return fsManager.resolveName(base, name.getBaseName()).getURI();
            }
        }
        catch (FileSystemException fse)
        {
            fse.printStackTrace();
            return null;
        }
    }

    public String getBasePath(String path)
    {
        if (UriParser.extractScheme(path) == null)
        {
            return super.getBasePath(path);
        }
        try
        {
            FileSystemManager fsManager = VFS.getManager();
            FileName name = fsManager.resolveURI(path);
            return name.getParent().getURI();
        }
        catch (FileSystemException fse)
        {
            fse.printStackTrace();
            return null;
        }
    }

    public String getFileName(String path)
    {
        if (UriParser.extractScheme(path) == null)
        {
            return super.getFileName(path);
        }
        try
        {
            FileSystemManager fsManager = VFS.getManager();
            FileName name = fsManager.resolveURI(path);
            return name.getBaseName();
        }
        catch (FileSystemException fse)
        {
            fse.printStackTrace();
            return null;
        }
    }

    public URL getURL(String basePath, String file) throws MalformedURLException
    {
        if ((basePath != null && UriParser.extractScheme(basePath) == null)
            || (basePath == null && UriParser.extractScheme(file) == null))
        {
            return super.getURL(basePath, file);
        }
        try
        {
            FileSystemManager fsManager = VFS.getManager();

            FileName path;
            if (basePath != null && UriParser.extractScheme(file) == null)
            {
                FileName base = fsManager.resolveURI(basePath);
                path = fsManager.resolveName(base, file);
            }
            else
            {
                path = fsManager.resolveURI(file);
            }

            URLStreamHandler handler = new VFSURLStreamHandler(path);
            return new URL(null, path.getURI(), handler);
        }
        catch (FileSystemException fse)
        {
            throw new ConfigurationRuntimeException("Could not parse basePath: " + basePath
                + " and fileName: " + file, fse);
        }
    }

    public URL locateFromURL(String basePath, String fileName)
    {
        String fileScheme = UriParser.extractScheme(fileName);

        // Use DefaultFileSystem if basePath and fileName don't have a scheme.
        if ((basePath == null || UriParser.extractScheme(basePath) == null) && fileScheme == null)
        {
            return super.locateFromURL(basePath, fileName);
        }
        try
        {
            FileSystemManager fsManager = VFS.getManager();

            FileObject file;
            // Only use the base path if the file name doesn't have a scheme.
            if (basePath != null && fileScheme == null)
            {
                String scheme = UriParser.extractScheme(basePath);
                FileSystemOptions opts = (scheme != null) ? getOptions(scheme) : null;
                FileObject base = (opts == null) ? fsManager.resolveFile(basePath)
                        : fsManager.resolveFile(basePath, opts);
                if (base.getType() == FileType.FILE)
                {
                    base = base.getParent();
                }

                file = fsManager.resolveFile(base, fileName);
            }
            else
            {
                FileSystemOptions opts = (fileScheme != null) ? getOptions(fileScheme) : null;
                file = (opts == null) ? fsManager.resolveFile(fileName)
                        : fsManager.resolveFile(fileName, opts);
            }

            if (!file.exists())
            {
                return null;
            }
            FileName path = file.getName();
            URLStreamHandler handler = new VFSURLStreamHandler(path);
            return new URL(null, path.getURI(), handler);
        }
        catch (FileSystemException fse)
        {
            return null;
        }
        catch (MalformedURLException ex)
        {
            return null;
        }
    }

    private FileSystemOptions getOptions(String scheme)
    {
        FileSystemOptions opts = new FileSystemOptions();
        FileSystemConfigBuilder builder;
        try
        {
            builder = VFS.getManager().getFileSystemConfigBuilder(scheme);
        }
        catch (Exception ex)
        {
            return null;
        }
        FileOptionsProvider provider = getFileOptionsProvider();
        if (provider != null)
        {
            Map map = provider.getOptions();
            if (map == null)
            {
                return null;
            }
            Iterator iter = map.entrySet().iterator();
            int count = 0;
            while (iter.hasNext())
            {
                Map.Entry entry = (Map.Entry) iter.next();
                try
                {
                    String key = (String) entry.getKey();
                    if (FileOptionsProvider.CURRENT_USER.equals(key))
                    {
                        key = "creatorName";
                    }
                    setProperty(builder, opts, key, entry.getValue());
                    ++count;
                }
                catch (Exception ex)
                {
                    // Ignore an incorrect property.
                    continue;
                }
            }
            if (count > 0)
            {
                return opts;
            }
        }
        return null;

    }

    private void setProperty(FileSystemConfigBuilder builder, FileSystemOptions options,
                             String key, Object value)
    {
        String methodName = "set" + key.substring(0, 1).toUpperCase() + key.substring(1);
        Class[] paramTypes = new Class[2];
        paramTypes[0] = FileSystemOptions.class;
        paramTypes[1] = value.getClass();

        try
        {
            Method method = builder.getClass().getMethod(methodName, paramTypes);
            Object[] params = new Object[2];
            params[0] = options;
            params[1] = value;
            method.invoke(builder, params);
        }
        catch (Exception ex)
        {
            return;
        }

    }

    /**
     * Stream handler required to create URL.
     */
    private static class VFSURLStreamHandler extends URLStreamHandler
    {
        /** The Protocol used */
        private final String protocol;

        public VFSURLStreamHandler(FileName file)
        {
            this.protocol = file.getScheme();
        }

        protected URLConnection openConnection(URL url) throws IOException
        {
            throw new IOException("VFS URLs can only be used with VFS APIs");
        }
    }
}