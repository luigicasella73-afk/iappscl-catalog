// Scarica i report mensili delle installazioni di Play Console dal bucket di Cloud Storage con la chiave
// dell'account di servizio (niente browser né login). Uso: java PlayReports.java <chiave.json> <cartella csv> <pacchetto>...
// Scarica i file installs_<pacchetto>_<aaaamm>_overview.csv mancanti, più sempre quelli del mese in corso e del precedente.
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class PlayReports {
    static final String BUCKET = "pubsite_prod_8895499783911530263";

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]++|\\\\.)*+)\"").matcher(json);   // possessivo: la chiave privata è lunga
        if (!m.find()) throw new IllegalStateException("manca " + name);
        return m.group(1).replace("\\n", "\n").replace("\\/", "/");
    }

    static String b64(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }

    public static void main(String[] a) throws Exception {
        String key = Files.readString(Path.of(a[0]));
        Path out = Path.of(a[1]);
        Files.createDirectories(out);
        String email = field(key, "client_email"), pem = field(key, "private_key");
        byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
        PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));

        long now = Instant.now().getEpochSecond();
        String head = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64(("{\"iss\":\"" + email + "\",\"scope\":\"https://www.googleapis.com/auth/devstorage.read_only\","
            + "\"aud\":\"https://oauth2.googleapis.com/token\",\"iat\":" + now + ",\"exp\":" + (now + 3000) + "}").getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(pk);
        s.update((head + "." + claims).getBytes(StandardCharsets.UTF_8));
        String jwt = head + "." + claims + "." + b64(s.sign());

        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<String> tok = http.send(HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + jwt))
            .build(), HttpResponse.BodyHandlers.ofString());
        if (tok.statusCode() != 200) throw new IllegalStateException("token: " + tok.statusCode() + " " + tok.body());
        String access = field(tok.body(), "access_token");

        DateTimeFormatter ym = DateTimeFormatter.ofPattern("yyyyMM");
        Set<String> recent = Set.of(LocalDate.now().format(ym), LocalDate.now().minusMonths(1).format(ym));
        int got = 0;
        for (int i = 2; i < a.length; i++) {
            String pkg = a[i];
            // elenco dei file di questa app nel bucket
            String prefix = "stats/installs/installs_" + pkg + "_";
            HttpResponse<String> list = http.send(HttpRequest.newBuilder(URI.create("https://storage.googleapis.com/storage/v1/b/" + BUCKET
                + "/o?fields=items(name)&prefix=" + URLEncoder.encode(prefix, StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + access).build(), HttpResponse.BodyHandlers.ofString());
            if (list.statusCode() != 200) throw new IllegalStateException("elenco " + pkg + ": " + list.statusCode() + " " + list.body());
            Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"(stats/installs/(installs_[^\"]+_(\\d{6})_overview\\.csv))\"").matcher(list.body());
            while (m.find()) {
                Path dest = out.resolve(m.group(2));
                if (Files.exists(dest) && !recent.contains(m.group(3))) continue;
                HttpResponse<byte[]> f = http.send(HttpRequest.newBuilder(URI.create("https://storage.googleapis.com/storage/v1/b/" + BUCKET
                    + "/o/" + URLEncoder.encode(m.group(1), StandardCharsets.UTF_8) + "?alt=media"))
                    .header("Authorization", "Bearer " + access).build(), HttpResponse.BodyHandlers.ofByteArray());
                if (f.statusCode() != 200) throw new IllegalStateException("file " + m.group(2) + ": " + f.statusCode());
                Files.write(dest, f.body());
                got++;
            }
        }
        System.out.println("SCARICATI " + got);
    }
}
