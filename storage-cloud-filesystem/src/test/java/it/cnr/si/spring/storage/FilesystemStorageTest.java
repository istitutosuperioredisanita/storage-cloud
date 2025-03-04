package it.cnr.si.spring.storage;

import it.cnr.si.spring.storage.bulk.StorageFile;
import it.cnr.si.spring.storage.config.StoragePropertyNames;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static it.cnr.si.spring.storage.config.StoragePropertyNames.DESCRIPTION;
import static it.cnr.si.spring.storage.config.StoragePropertyNames.TITLE;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@ContextConfiguration("classpath:storage-filesystem-test-context.xml")
@TestPropertySource("classpath:META-INF/spring/filesystem.properties")
public class FilesystemStorageTest {

    private final Logger log = LoggerFactory.getLogger(FilesystemStorageTest.class);

    @Autowired
    private StoreService storeService;
    @Autowired
    private StorageDriver storageDriver;

    private static final String PIPPO = "pippo",
                                PLUTO = "pluto";

    @Test
    public void testCorrectStorType() {
        assertEquals(StorageDriver.StoreType.FILESYSTEM, storageDriver.getStoreType());
    }

    @Test
    public void testCreateAndReadDirectoryAndMetadata() {
        String TITOLO = "Titolo Uno";
        String DESCRIZIONE = "Descrizione Uno";
        String folder = storeService.createFolderIfNotPresent("/", "uno", TITOLO, DESCRIZIONE);

        StorageObject so = storeService.getStorageObjectBykey(folder);

        log.info("{}", so);

        assertNotNull(so);
        assertEquals( TITOLO, so.getPropertyValue(TITLE.value()) );
        assertEquals( DESCRIZIONE, so.getPropertyValue(DESCRIPTION.value()) );
        assertEquals( folder, so.getKey() );
        assertEquals( folder, so.getPath() );

        folder = storeService.createFolderIfNotPresent("", "due", TITOLO, DESCRIZIONE);
        so = storeService.getStorageObjectBykey(folder);

        log.info("{}", so);

        assertNotNull(so);
        assertEquals( TITOLO, so.getPropertyValue(TITLE.value()) );
        assertEquals( DESCRIZIONE, so.getPropertyValue(DESCRIPTION.value()) );
        assertEquals( folder, so.getKey() );
        assertEquals( folder, so.getPath() );

        try {
            folder = storeService.createFolderIfNotPresent(null, "due", TITOLO, DESCRIZIONE);
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
    }


    @Test
    public void testCreateAndReadSimpleDocumentAndMetadata() {
        InputStream is = new ByteArrayInputStream(PIPPO.getBytes());
        String contentType = "text/plain";
        String path = "";
        Map<String, Object> metadata = new HashMap<>();
        StorageObject so = storeService.storeSimpleDocument(is, contentType, path, metadata);

        StorageObject tre = storeService.getStorageObjectBykey(so.getKey());
        StorageObject quattro = storeService.getStorageObjectByPath(so.getPath());

        log.info("{}", so);
        log.info("{}", tre);
        log.info("{}", quattro);

        assertEquals(so.toString(), tre.toString());
        assertEquals(so.toString(), quattro.toString());
        assertEquals(so.getPath(), tre.getPath());
        assertEquals(so.getKey(), tre.getKey());

        InputStream content = storeService.getResource(so.getKey());
        so= storeService.getStorageObjectBykey(so.getKey());
        BigInteger length= so.<BigInteger>getPropertyValue(StoragePropertyNames.CONTENT_STREAM_LENGTH.value());
        String ctype = so.<String>getPropertyValue(StoragePropertyNames.CONTENT_STREAM_MIME_TYPE.value());
        assertNotEquals(length,0);
        assertNotNull(ctype);


        String contentString = new Scanner(content).next();

        log.info("{}", contentString);

        assertEquals(PIPPO, contentString);

    }

    @Test
    public void testRestoreSimpleDocument() {

        StorageObject so = createFile();

        log.info("{}", so);
        StorageObject quattro = storeService.getStorageObjectByPath(so.getPath());

        log.info("{}", quattro);

        assertEquals(so.getPath(), quattro.getPath());
        assertEquals(so.getKey(), quattro.getKey());

        InputStream content = storeService.getResource(so.getKey());
        String contentString = new Scanner(content).next();

        log.info("{}", contentString);

        assertEquals(PIPPO, contentString);
    }

    @Test
    public void testUpdateProperties() {

        StorageObject so = createFile();

        so = storeService.getStorageObjectBykey(so.getKey());

        assertNull(so.getPropertyValue("pluto"));

        Map<String, Object> newProperties = new HashMap<>();
        newProperties.put("pluto", "paperino");
        newProperties.put(StoragePropertyNames.NAME.value(), "TitoloNew");
        storeService.updateProperties(newProperties, so);

        so = storeService.getStorageObjectBykey(so.getKey());

        assertEquals("paperino", so.getPropertyValue("pluto"));

    }

    @Test
    public void testUpdateStream() {

        StorageObject so = createFile();

        so = storeService.getStorageObjectBykey(so.getKey());
        InputStream content = storeService.getResource(so.getKey());
        String contentString = new Scanner(content).next();

        assertEquals(PIPPO, contentString);

        InputStream is = new ByteArrayInputStream(PLUTO.getBytes());
        storeService.updateStream(so.getKey(), is, "text/plain");

        so = storeService.getStorageObjectBykey(so.getKey());
        content = storeService.getResource(so.getKey());
        contentString = new Scanner(content).next();

        assertEquals(PLUTO, contentString);

        try {
            storeService.updateStream("key-non-esistente", is, "text/plain");
            fail("File should not be updated because it should not exist");
        } catch (StorageException e) {
            assertEquals(StorageException.Type.NOT_FOUND, e.getType());
        }
    }

    @Test
    public void testDeleteFile() {

        StorageObject so = createFile();

        so = storeService.getStorageObjectBykey(so.getKey());
        assertTrue( storeService.delete(so.getKey()) );
        assertNull( storeService.getStorageObjectBykey(so.getKey()) );

    }

    @Test
    public void testDeleteEmptyDirectory() {

        String folder = storeService.createFolderIfNotPresent("/",
                "deleteme",
                "Da Cancellare",
                "Questo Folder esiste solo per essere cancellato");

        StorageObject so = storeService.getStorageObjectBykey(folder);
        assertEquals("Da Cancellare", so.getPropertyValue("cm:title"));

        assertTrue( storeService.delete(folder) );
        assertNull( storeService.getStorageObjectBykey(folder) );

    }

    @Test
    public void testDeleteNonEmptyDirectory() {

        String folder = storeService.createFolderIfNotPresent("/",
                "parent",
                "Superiore",
                "Questo Folder esiste solo per contenerne un'altro");

        String subfolder = storeService.createFolderIfNotPresent(folder,
                "child",
                "Sub",
                "Questo Folder esiste solo per essere contenuto");

        StorageObject so = storeService.getStorageObjectBykey(subfolder);

        assertEquals("Sub", so.getPropertyValue("cm:title"));

        assertTrue(storeService.delete(folder));
        assertNull( storeService.getStorageObjectBykey(folder) );
        assertNull( storeService.getStorageObjectBykey(subfolder) );

    }

    private StorageObject createFile() {
        InputStream is = new ByteArrayInputStream(PIPPO.getBytes());

        StorageFile file = new StorageFile(is,
                "text/plain",
                "Titolo");

        return storeService.restoreSimpleDocument(
                file,
                new ByteArrayInputStream(file.getBytes()),
                "text/plain",
                "Titolo",
                "/Comunicazioni da ISS/000.000/Contratti/2025/CONTRATTOTEST/",
                true);
    }
    @Test
    public void testUpdatePropertiesDirectory() {

        String keyFolder ="\\Comunicazioni da ISS\\000.000\\Contratti\\2025\\Contratto 2025D000000011";
        StorageObject so=storeService.getStorageObjectBykey( keyFolder);
        so = storeService.getStorageObjectBykey(so.getKey());

        assertNull(so.getPropertyValue("pluto"));

        Map<String, Object> newProperties = new HashMap<>();

        newProperties.put(StoragePropertyNames.NAME.value(), "Contratto 2025C000000011");
        storeService.updateProperties(newProperties, so);

        so = storeService.getStorageObjectBykey("\\Comunicazioni da ISS\\000.000\\Contratti\\2025\\Contratto 2025C000000011");

        assertEquals("paperino", so.getPropertyValue("pluto"));


    }

    @Test
    public void testFileProperties() {

        File dir = new File("E:\\sigla\\Comunicazioni da ISS\\000.000\\Contratti\\2025\\Contratto 2025D000000011");
        String rootFolder ="E:\\sigla\\\\Comunicazioni da ISS\\000.000\\Contratti\\2025\\Contratto 2025D000000011";
        String keyFolder ="\\Comunicazioni da ISS\\000.000\\Contratti\\2025\\Contratto 2025D000000011";
        List<String> pathStr = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(rootFolder))) {
            pathStr = paths.filter(Files::isDirectory)
                    .map(path -> path.toString())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();

        }
        List<StorageObject> list=storeService.getChildren( keyFolder);
        pathStr.stream().forEach(path->{
            StorageObject soDir=storeService.getStorageObjectBykey(path);
            List<StorageObject> listFiles  =storeService.getChildren( soDir.getKey());
        });


    }
    @Test
    public void copyNode() {

        StorageObject source = storeService.getStorageObjectBykey("/Comunicazioni da ISS/000.000/Contratti/2025/Contratto 2025P000000012/CTR-000.000-2025P000000012.docuementi_ele_da_registrare.xlsx");
        StorageObject target = new StorageObject(
                "/Comunicazioni da ISS/000.000/Contratti/Passivo/Contratto/",
                "/Comunicazioni da ISS/000.000/Contratti/Passivo/Contratto/",
                null);
        storeService.copyNode(source, target);
    }

}
