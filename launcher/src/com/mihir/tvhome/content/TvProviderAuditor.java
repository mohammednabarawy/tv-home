package com.mihir.tvhome.content;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Dedicated read-only TvProvider verification engine.
 * Executes queries strictly under the launcher application's actual UID and context,
 * recording:
 * - Query status (SUCCESS_WITH_DATA, SUCCESS_EMPTY, PERMISSION_DENIED, QUERY_ERROR)
 * - Row count
 * - Available column schema
 * - SecurityException / permission details if any
 * - Output written to local JSON report in internal files directory for audit inspection.
 */
public class TvProviderAuditor {

    private static final String TAG = "TvProviderAuditor";

    public static class AuditEntry {
        public String uri;
        public String authority;
        public String status;
        public int rowCount;
        public List<String> columns = new ArrayList<>();
        public String errorDetails;

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"uri\":\"").append(escape(uri)).append("\",");
            sb.append("\"authority\":\"").append(escape(authority)).append("\",");
            sb.append("\"status\":\"").append(status).append("\",");
            sb.append("\"rowCount\":").append(rowCount).append(",");
            sb.append("\"columns\":[");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(columns.get(i))).append("\"");
            }
            sb.append("],");
            sb.append("\"errorDetails\":").append(errorDetails != null ? "\"" + escape(errorDetails) + "\"" : "null");
            sb.append("}");
            return sb.toString();
        }
    }

    public static class AuditReport {
        public int callingUid;
        public String callingPackage;
        public long timestamp;
        public List<AuditEntry> entries = new ArrayList<>();

        public String toJson() {
            StringBuilder sb = new StringBuilder("{\n");
            sb.append("  \"callingUid\": ").append(callingUid).append(",\n");
            sb.append("  \"callingPackage\": \"").append(escape(callingPackage)).append("\",\n");
            sb.append("  \"timestamp\": ").append(timestamp).append(",\n");
            sb.append("  \"entries\": [\n");
            for (int i = 0; i < entries.size(); i++) {
                sb.append("    ").append(entries.get(i).toJson());
                if (i < entries.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n}");
            return sb.toString();
        }
    }

    private static final String[] TARGET_URIS = {
            "content://android.media.tv/preview_program",
            "content://android.media.tv/watch_next_program",
            "content://android.media.tv/channel",
            "content://android.media.tv/program"
    };

    /**
     * Executes the read-only audit and persists results into internal storage.
     */
    public static AuditReport runAudit(Context context) {
        AuditReport report = new AuditReport();
        report.callingUid = Process.myUid();
        report.callingPackage = context.getPackageName();
        report.timestamp = System.currentTimeMillis();

        Log.i(TAG, "Starting TvProvider audit from App UID: " + report.callingUid + " (" + report.callingPackage + ")");

        for (String uriStr : TARGET_URIS) {
            AuditEntry entry = new AuditEntry();
            entry.uri = uriStr;
            Uri parsed = Uri.parse(uriStr);
            entry.authority = parsed.getAuthority();

            try (Cursor cursor = context.getContentResolver().query(parsed, null, null, null, null)) {
                if (cursor != null) {
                    entry.rowCount = cursor.getCount();
                    if (entry.rowCount > 0) {
                        entry.status = "SUCCESS_WITH_DATA";
                    } else {
                        entry.status = "SUCCESS_EMPTY";
                    }
                    entry.columns = Arrays.asList(cursor.getColumnNames());
                    Log.i(TAG, "Query " + uriStr + " -> " + entry.status + " (Rows: " + entry.rowCount + ")");
                } else {
                    entry.status = "SUCCESS_EMPTY";
                    entry.rowCount = 0;
                    entry.columns = Collections.emptyList();
                    Log.i(TAG, "Query " + uriStr + " -> Null cursor (0 rows)");
                }
            } catch (SecurityException se) {
                entry.status = "PERMISSION_DENIED";
                entry.errorDetails = se.getClass().getSimpleName() + ": " + se.getMessage();
                Log.w(TAG, "Query " + uriStr + " -> PERMISSION_DENIED: " + entry.errorDetails);
            } catch (Exception e) {
                entry.status = "QUERY_ERROR";
                entry.errorDetails = e.getClass().getSimpleName() + ": " + e.getMessage();
                Log.e(TAG, "Query " + uriStr + " -> QUERY_ERROR: " + entry.errorDetails);
            }

            report.entries.add(entry);
        }

        // Write report file
        try {
            File outFile = new File(context.getFilesDir(), "tvprovider_app_uid_audit.json");
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
                writer.write(report.toJson());
            }
            Log.i(TAG, "Saved audit report to: " + outFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to write audit file", e);
        }

        return report;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
