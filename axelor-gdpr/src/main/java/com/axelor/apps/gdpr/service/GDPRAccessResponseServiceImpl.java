package com.axelor.apps.gdpr.service;

import com.axelor.apps.base.db.AdvancedExport;
import com.axelor.apps.base.db.AdvancedExportLine;
import com.axelor.apps.base.db.repo.AdvancedExportLineRepository;
import com.axelor.apps.base.db.repo.AdvancedExportRepository;
import com.axelor.apps.base.service.advancedExport.AdvancedExportService;
import com.axelor.apps.gdpr.db.GDPRAccessResponse;
import com.axelor.apps.gdpr.db.GDPRRequest;
import com.axelor.apps.gdpr.db.repo.GDPRAccessResponseRepository;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.db.repo.TemplateRepository;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.TemplateMessageServiceImpl;
import com.axelor.auth.db.AuditableModel;
import com.axelor.common.Inflector;
import com.axelor.db.Query;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.db.mapper.PropertyType;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.GDPRDMSFileRepository;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.json.JSONException;

public class GDPRAccessResponseServiceImpl implements GDPRAccessResponseService {

  public static final String OUTPUT_FILE_NAME = "data";
  public static final String OUTPUT_EXT = ".zip";
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected GDPRAccessResponseRepository gdprAccessResponseRepository;
  protected MetaModelRepository metaModelRepo;
  protected GDPRResponseService gdprResponseService;
  protected AdvancedExportService advancedExportService;
  protected AdvancedExportLineRepository advancedExportLineRepository;
  protected AdvancedExportRepository advancedExportRepository;
  protected MessageService messageService;

  @Inject
  public GDPRAccessResponseServiceImpl(
      GDPRAccessResponseRepository gdprAccessResponseRepository,
      MetaModelRepository metaModelRepo,
      GDPRResponseService gdprResponseService,
      AdvancedExportService advancedExportService,
      AdvancedExportLineRepository advancedExportLineRepository,
      AdvancedExportRepository advancedExportRepository,
      MessageService messageService) {
    this.gdprAccessResponseRepository = gdprAccessResponseRepository;
    this.metaModelRepo = metaModelRepo;
    this.gdprResponseService = gdprResponseService;
    this.advancedExportService = advancedExportService;
    this.advancedExportLineRepository = advancedExportLineRepository;
    this.advancedExportRepository = advancedExportRepository;
    this.messageService = messageService;
  }

  @SuppressWarnings("unchecked")
  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public GDPRAccessResponse generateAccessResponseDataFile(GDPRRequest gdprRequest)
      throws AxelorException, ClassNotFoundException, IOException {

    List<File> files = new ArrayList<>();
    GDPRAccessResponse gdprAccessResponse = new GDPRAccessResponse();

    Class<? extends AuditableModel> modelSelectKlass =
        (Class<? extends AuditableModel>) Class.forName(gdprRequest.getModelSelect());

    AuditableModel selectedModel =
        Query.of(modelSelectKlass).filter("id = " + gdprRequest.getModelId()).fetchOne();

    MetaModel metaModel = metaModelRepo.findByName(modelSelectKlass.getSimpleName());

    gdprResponseService
        .getEmailFromPerson(selectedModel)
        .ifPresent(gdprAccessResponse::setResponseEmailAddress);

    files.addAll(generateRelatedObjectsCSV(gdprRequest));

    files.addAll(generateCSVForThePerson(gdprRequest, modelSelectKlass, metaModel, selectedModel));

    files.addAll(searchDMSFile(gdprRequest));

    String exportFileName =
        OUTPUT_FILE_NAME
            .concat(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
            .concat(OUTPUT_EXT);

    File zip = new File(exportFileName);
    MetaFile dataMetaFile = new MetaFile();
    dataMetaFile.setFileName(exportFileName);

    addFilesToZip(zip, files);
    Beans.get(MetaFiles.class).upload(zip, dataMetaFile);
    gdprAccessResponse.setDataFile(dataMetaFile);
    gdprAccessResponse.setGdprRequest(gdprRequest);
    gdprAccessResponseRepository.save(gdprAccessResponse);
    gdprRequest.setAccessResponse(gdprAccessResponse);
    Beans.get(GDPRAccessResponseRepository.class).save(gdprAccessResponse);

    return gdprAccessResponse;
  }

  /**
   * generate csv file for choosed model (lead/partner)
   *
   * @param gdprRequest
   * @return
   * @throws AxelorException
   * @throws ClassNotFoundException
   * @throws IOException
   */
  public List<File> generateCSVForThePerson(
      GDPRRequest gdprRequest,
      Class<?> modelSelectKlass,
      MetaModel metaModel,
      AuditableModel selectedModel)
      throws AxelorException, ClassNotFoundException, IOException {
    List<File> generatedFiles = new ArrayList<>();

    List<AuditableModel> list = new ArrayList<>();

    Mapper mapper = Mapper.of(selectedModel.getClass());

    list.add(selectedModel);
    generatedFiles.add(createCSV(list, metaModel));

    List<Property> binaryProperties =
        Arrays.stream(mapper.getProperties())
            .filter(property -> PropertyType.BINARY.equals(property.getType()))
            .collect(Collectors.toList());

    // check for pictures
    for (Property property : binaryProperties) {
      String fileName =
          modelSelectKlass.getSimpleName()
              + "_"
              + property.getName()
              + "_"
              + gdprRequest.getModelId()
              + ".png";
      Object value = mapper.get(selectedModel, property.getName());
      if (Objects.nonNull(value)) {
        String base64Img = new String((byte[]) value);
        String base64ImgData = base64Img.split(",")[1];
        byte[] img = Base64.getDecoder().decode(base64ImgData);
        ByteArrayInputStream inImg = new ByteArrayInputStream(img);

        MetaFile picture = Beans.get(MetaFiles.class).upload(inImg, fileName);

        File file = MetaFiles.getPath(picture).toFile();

        generatedFiles.add(file);
      }
    }

    return generatedFiles;
  }

  /**
   * generate csv file for related objects
   *
   * @param gdprRequest
   * @return
   * @throws ClassNotFoundException
   * @throws IOException
   * @throws AxelorException
   */
  @SuppressWarnings("unchecked")
  public List<File> generateRelatedObjectsCSV(GDPRRequest gdprRequest)
      throws ClassNotFoundException, AxelorException {
    List<File> generatedFiles = new ArrayList<>();

    List<MetaField> metaFields = gdprResponseService.selectMetaFields(gdprRequest);

    for (MetaField metaField : metaFields) {
      Class<? extends AuditableModel> klass =
          (Class<? extends AuditableModel>) Class.forName(metaField.getMetaModel().getFullName());
      List<? extends AuditableModel> records;

      if (!"OneToMany".equals(metaField.getRelationship())) {
        records =
            Query.of(klass).filter(metaField.getName() + " = " + gdprRequest.getModelId()).fetch();

      } else {
        records =
            Query.of(klass)
                .filter(gdprRequest.getModelId() + " MEMBER OF " + metaField.getName())
                .fetch();
      }
      if (records.size() > 1) {
        LOG.debug("records : {}", records);
      }
      if (!records.isEmpty()) {
        File csvFile = createCSV(records, metaField.getMetaModel());
        generatedFiles.add(csvFile);
      }
    }

    return generatedFiles;
  }

  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public File createCSV(List<? extends AuditableModel> references, MetaModel metaModel)
      throws ClassNotFoundException, AxelorException {
    AdvancedExport advancedExport = new AdvancedExport();
    advancedExport.setMetaModel(metaModel);
    advancedExport.setIncludeArchivedRecords(true);
    advancedExport.setAdvancedExportLineList(getAllFields(advancedExport));
    advancedExportRepository.save(advancedExport);
    File file = null;
    List<Long> recordsIds = new ArrayList<>();

    for (Object reference : references) {
      recordsIds.add((Long) Mapper.of(reference.getClass()).get(reference, "id"));
    }

    file = advancedExportService.export(advancedExport, recordsIds, AdvancedExportService.CSV);

    return file;
  }

  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public List<AdvancedExportLine> getAllFields(AdvancedExport advancedExport)
      throws ClassNotFoundException {

    Inflector inflector;
    inflector = Inflector.getInstance();

    if (advancedExport.getMetaModel() == null) {
      return Collections.emptyList();
    }

    List<AdvancedExportLine> advancedExportLineList = new ArrayList<>();
    MetaModelRepository metaModelRepository = Beans.get(MetaModelRepository.class);
    MetaFieldRepository metaFieldRepository = Beans.get(MetaFieldRepository.class);

    for (MetaField field : advancedExport.getMetaModel().getMetaFields()) {
      AdvancedExportLine advancedExportLine = new AdvancedExportLine();
      advancedExportLine.setCurrentDomain(advancedExport.getMetaModel().getName());

      Class<?> modelClass = Class.forName(advancedExport.getMetaModel().getFullName());
      Mapper modelMapper = Mapper.of(modelClass);

      if (modelMapper.getProperty(field.getName()) == null
          || modelMapper.getProperty(field.getName()).isTransient()) {
        continue;
      }

      if (!Strings.isNullOrEmpty(field.getRelationship())) {
        MetaModel metaModel =
            metaModelRepository.all().filter("self.name = ?", field.getTypeName()).fetchOne();

        Class<?> klass = Class.forName(metaModel.getFullName());
        Mapper mapper = Mapper.of(klass);
        String fieldName = mapper.getNameField() == null ? "id" : mapper.getNameField().getName();
        MetaField metaField =
            metaFieldRepository
                .all()
                .filter("self.name = ?1 AND self.metaModel = ?2", fieldName, metaModel)
                .fetchOne();
        advancedExportLine.setMetaField(metaField);
        advancedExportLine.setTargetField(field.getName() + "." + metaField.getName());
      } else {
        advancedExportLine.setMetaField(field);
        advancedExportLine.setTargetField(field.getName());
      }

      if (Strings.isNullOrEmpty(field.getLabel())) {
        advancedExportLine.setTitle(inflector.humanize(field.getName()));
      } else {
        advancedExportLine.setTitle(field.getLabel());
      }
      advancedExportLine.setAdvancedExport(advancedExport);
      advancedExportLineRepository.save(advancedExportLine);
      advancedExportLineList.add(advancedExportLine);
    }
    return advancedExportLineList;
  }

  /**
   * add files to archive
   *
   * @param source
   * @param files
   * @throws IOException
   */
  public void addFilesToZip(File source, List<File> files) throws IOException {
    MetaFiles.createTempFile(null, OUTPUT_EXT);

    File tmpZip = File.createTempFile(source.getName(), null);

    byte[] buffer = new byte[1024];

    try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(tmpZip.toPath()))) {
      try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(source.toPath()))) {
        for (File f : files) {
          try (InputStream in = Files.newInputStream(f.toPath())) {
            out.putNextEntry(new ZipEntry(f.getName()));
            for (int read = in.read(buffer); read > -1; read = in.read(buffer)) {
              out.write(buffer, 0, read);
            }
            out.closeEntry();
          }
        }

        for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
          out.putNextEntry(ze);
          for (int read = zin.read(buffer); read > -1; read = zin.read(buffer)) {
            out.write(buffer, 0, read);
          }
          out.closeEntry();
        }

        Files.delete(tmpZip.toPath());
      }
    }
  }

  /**
   * add linked dmsFiles
   *
   * @param gdprRequest
   * @return
   */
  protected List<File> searchDMSFile(GDPRRequest gdprRequest) {

    List<DMSFile> dmsFiles =
        Beans.get(GDPRDMSFileRepository.class)
            .findByModel(gdprRequest.getModelId(), gdprRequest.getModelSelect());

    return dmsFiles.stream()
        .map(dmsFile -> MetaFiles.getPath(dmsFile.getMetaFile()).toFile())
        .filter(File::exists)
        .collect(Collectors.toList());
  }

  @Override
  public boolean sendEmailResponse(GDPRAccessResponse gdprAccessResponse)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException,
          AxelorException, IOException, JSONException {

    Template template =
        Beans.get(TemplateRepository.class)
            .all()
            .filter(
                "self.metaModel = ?",
                metaModelRepo.findByName(gdprAccessResponse.getClass().getSimpleName()))
            .fetchOne();

    if (Objects.nonNull(template)) {

      Message message =
          Beans.get(TemplateMessageServiceImpl.class).generateMessage(gdprAccessResponse, template);
      Set<MetaFile> metaFiles = Sets.newHashSet();
      metaFiles.add(gdprAccessResponse.getDataFile());
      messageService.attachMetaFiles(message, metaFiles);
      messageService.sendMessage(message);
      gdprAccessResponse.setSendingDateTime(LocalDateTime.now());
      gdprAccessResponse.setResponseMessage(message);
      return true;
    }
    return false;
  }
}
