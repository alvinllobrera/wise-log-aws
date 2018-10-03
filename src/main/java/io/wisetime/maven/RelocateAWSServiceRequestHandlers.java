package io.wisetime.maven;

import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.codehaus.plexus.util.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Class that updates AWS request.handlers with realocated package name.
 */
public class RelocateAWSServiceRequestHandlers implements ResourceTransformer {

  private static final String HANDLERS_PATH = "/com/amazonaws/services/logs/request.handlers";

  private final Map<String, Set<String>> serviceEntries = new LinkedHashMap<>();

  public RelocateAWSServiceRequestHandlers() {
  }

  public boolean canTransformResource(String resource) {
    return resource.contains(HANDLERS_PATH);
  }

  public void processResource(String resource, InputStream is, List<Relocator> relocators)
      throws IOException {
    Set<String> serviceLines = getServiceLines(resource);

    for (String line : readAllLines(is)) {
      if (!line.isEmpty()) {
        serviceLines.add(relocateIfPossible(relocators, line));
      }
    }

    is.close();
  }

  private Set<String> getServiceLines(String resource) {
    Set<String> lines = serviceEntries.get(resource);
    if (lines == null) {
      lines = new LinkedHashSet<>();
      serviceEntries.put(resource, lines);
    }
    return lines;
  }

  private String[] readAllLines(InputStream is) throws IOException {
    return IOUtil.toString(is, "utf-8").replace('\r', '|').replace('\n', '|').split("\\|");
  }

  private String relocateIfPossible(List<Relocator> relocators, String line) {
    for (Relocator relocator : relocators) {
      if (relocator.canRelocateClass(line)) {
        return relocator.relocateClass(line);
      }
    }
    return line;
  }

  public boolean hasTransformedResource() {
    return !serviceEntries.isEmpty();
  }

  public void modifyOutputStream(JarOutputStream jos)
      throws IOException {
    for (Entry<String, Set<String>> entry : serviceEntries.entrySet()) {
      jos.putNextEntry(new JarEntry(entry.getKey()));
      jos.write(toResourceBytes(entry.getValue()));
    }
  }

  private byte[] toResourceBytes(Set<String> value) throws IOException {
    StringBuilder builder = new StringBuilder();
    for (String line : value) {
      builder.append(line).append('\n');
    }
    return builder.toString().getBytes("utf-8");
  }
}
