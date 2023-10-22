package com.emanuelef.remote_capture.dlg;

import static com.emanuelef.remote_capture.CaptureService.asyncResumeConnection;

import static org.chromium.base.ContextUtils.getApplicationContext;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.emanuelef.remote_capture.AppsResolver;
import com.emanuelef.remote_capture.CaptureService;
import com.emanuelef.remote_capture.R;
import com.emanuelef.remote_capture.activities.MainActivity;
import com.emanuelef.remote_capture.model.AppDescriptor;
import com.emanuelef.remote_capture.model.ConnectionDescriptor;
import java.util.concurrent.CompletableFuture;

public class ConnectionAlertDlg {

    private int app_uid;
    private final int conn_id;
    public ConnectionAlertDlg(MainActivity mainActivity, int app_uid, int conn_id) {
        this.app_uid = app_uid;
        this.conn_id = conn_id; // Assign the value in the constructor
    }

    public void showConnectionAlert(Context context, String message) {
       // Context c=getApplicationContext();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.resume_connection_dialog, null);
        builder.setView(view);

        ImageView appIconImageView = view.findViewById(R.id.appIconImageView);
        TextView appNameTextView = view.findViewById(R.id.appNameTextView);
        TextView dialogMessageTextView = view.findViewById(R.id.dialogMessageTextView);
        Button blockButton = view.findViewById(R.id.blockButton);
        Button resumeButton = view.findViewById(R.id.resumeButton);

        int uid = this.app_uid;
        AppsResolver resolver = new AppsResolver(context);
        AppDescriptor app = resolver.getAppByUid(this.app_uid, 0);
        if (app == null) {
            return;
        }

        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = packageManager.getApplicationInfo(app.getPackageName(), 0);
            Drawable appIcon = packageManager.getApplicationIcon(applicationInfo);
            String appName = (String) packageManager.getApplicationLabel(applicationInfo);

            appIconImageView.setImageDrawable(appIcon);
            appNameTextView.setText(appName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        dialogMessageTextView.setText(message);

        AlertDialog connectionAlertDlg = builder.create();
        connectionAlertDlg.show();

        blockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle block action

                // Call your block function here
               // CaptureService.blockConnection(conn_id);
                CaptureService.getCaptureService().hashSet.add(conn_id);
                CompletableFuture<Void> pauseFuture = asyncResumeConnection(conn_id);
                connectionAlertDlg.dismiss();
            }
        });

        resumeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle resume action asynchronously
                CompletableFuture<Void> pauseFuture = asyncResumeConnection(conn_id);
                connectionAlertDlg.dismiss();
            }
        });
    }


}
