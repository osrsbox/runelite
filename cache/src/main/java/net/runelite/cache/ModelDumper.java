/*
* Copyright (c) 2019, PH01L <phoil@osrsbox.com>
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice, this
*    list of conditions and the following disclaimer.
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
* ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
* WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
* (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
* LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
* SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package net.runelite.cache;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.models.ObjExporter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class ModelDumper
{
    private final Store store;

    public static void main(String[] args) throws IOException
    {
        Options options = new Options();
        options.addOption("c", "cache", true, "cache location");
        options.addOption(null, "models", true, "directory to dump models to");
        options.addOption(null, "convert", false, "convert extracted models (requires -models)");

        System.out.println(">>> Starting ModelDumper...");
        System.out.println("  > Parsing command line arguments...");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try
        {
            cmd = parser.parse(options, args);
        }
        catch (ParseException ex)
        {
            System.err.println("Error parsing command line options: " + ex.getMessage());
            System.exit(-1);
        }

        if (!cmd.hasOption("models"))
        {
            System.out.println(">>> ERROR: You must provide a directory for model extraction...");
            System.out.println("    -models <output_directory-for-files>");
            System.exit(-1);
        }

        System.out.println(">>> Locating cache...");
        String cache;

        if (cmd.hasOption("cache"))
        {
            System.out.println("  > Cache location provided as command line argument...");
            cache = cmd.getOptionValue("cache");
            System.out.println("  > " + cache);
        }
        else
        {
            System.out.println("  > Cache location not provided...");
            System.out.println("  > Attempting automatic location...");
            String userHomeDir = System.getProperty("user.home");
            cache = userHomeDir + File.separator + "jagexcache" + File.separator + "oldschool" + File.separator + "LIVE" + File.separator;
            System.out.println("  > Checking for cache in following location:");
            System.out.println("  > " + cache);
        }

        System.out.println(">>> Verifying cache directory contents...");
        boolean foundValidCache = false;

        File f = new File(cache);
        if ((f.exists()) && (f.isDirectory()))
        {
            File[] listOfFiles = new File(cache).listFiles();
            for (int i = 0; i < listOfFiles.length; i++)
            {
                if (listOfFiles[i].isFile()) {
                    String fileName = listOfFiles[i].getName();
                    String fileNameBase = fileName.substring(0, fileName.lastIndexOf('.'));

                    if (fileNameBase.equals("main_file_cache"))
                    {
                        foundValidCache = true;
                        System.out.println("  > SUCCESS: Cache found and validated..." + System.lineSeparator());
                        break;
                    }
                }
            }
        }

        if (!foundValidCache)
        {
            System.out.println("  > ERROR: Invalid cache directory. Exiting." + System.lineSeparator());
            System.exit(-1);
        }

        System.out.println(">>> Loading cache...");
        Store store = loadStore(cache);

        if (cmd.hasOption("models"))
        {
            String modelsdir = cmd.getOptionValue("models");

            boolean convertModels = false;
            if (cmd.hasOption("convert"))
            {
                convertModels = true;
            }

            System.out.println(">>> Dumping models to " + modelsdir);
            dumpModels(store, new File(modelsdir), convertModels);
            System.exit(0);
        }
        else
        {
            System.err.println(">>> Nothing to do. Exiting.");
            System.exit(-1);
        }
    }

    private ModelDumper(Store store)
    {
        this.store = store;
    }

    private static Store loadStore(String cache) throws IOException
    {
        Store store = new Store(new File(cache));
        store.load();
        return store;
    }

    private static void dumpModels(Store store, File modelsdir, boolean convertModels) throws IOException
    {
        System.out.println(">>> Starting model dumper...");

        ModelDumper dumper = new ModelDumper(store);

        System.out.println("  > Dumping models...");
        dumper.extract(modelsdir, convertModels);

        System.out.println(">>> Model dumper finished!");
    }

    public void extract(File modelDir, boolean convertModels) throws IOException
    {
        store.load();

        int count = 0;

        modelDir.mkdirs();

        Storage storage = store.getStorage();
        Index index = store.getIndex(IndexType.MODELS);

        for (Archive archive : index.getArchives())
        {
            byte[] contents = archive.decompress(storage.loadArchive(archive));

            ModelLoader loader = new ModelLoader();
            loader.load(archive.getArchiveId(), contents);

            int indexNumber = archive.getArchiveId();
            String outFileName = modelDir + File.separator + indexNumber + ".model";
            Path path = Paths.get(outFileName, new String[0]);
            Files.write(path, contents, new OpenOption[0]);

            if (convertModels)
            {
                convert(modelDir, indexNumber);
            }
            count++;
        }
        System.out.println(">>> Dumped models:" + count);
    }

    public void convert(File modelDir, int indexNumber) throws IOException
    {
        TextureManager tm = new TextureManager(store);
        tm.load();
        ModelLoader loader = new ModelLoader();

        String modelDirPath = modelDir.getPath();
        String modelFileAbsolutePath = modelDirPath + File.separator + indexNumber + ".model";

        ModelDefinition model = loader.load(indexNumber, Files.readAllBytes(new File(modelFileAbsolutePath).toPath()));
        ObjExporter exporter = new ObjExporter(tm, model);
        String objFileOut = modelDirPath + File.separator + indexNumber + ".obj";
        String mtlFileOut = modelDirPath + File.separator + indexNumber + ".mtl";
        try (PrintWriter objWriter = new PrintWriter(new FileWriter(new File(objFileOut)));
        PrintWriter mtlWriter = new PrintWriter(new FileWriter(new File(mtlFileOut))))
        {
            exporter.export(objWriter, mtlWriter);
        }
    }
}
