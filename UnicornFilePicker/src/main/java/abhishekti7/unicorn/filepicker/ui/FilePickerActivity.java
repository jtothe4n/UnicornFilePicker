package abhishekti7.unicorn.filepicker.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import abhishekti7.unicorn.filepicker.R;
import abhishekti7.unicorn.filepicker.adapters.DirectoryAdapter;
import abhishekti7.unicorn.filepicker.adapters.DirectoryStackAdapter;
import abhishekti7.unicorn.filepicker.databinding.UnicornActivityFilePickerBinding;
import abhishekti7.unicorn.filepicker.models.Config;
import abhishekti7.unicorn.filepicker.models.DirectoryModel;
import abhishekti7.unicorn.filepicker.utils.UnicornSimpleItemDecoration;

/**
 * Created by Abhishek Tiwari on 06-01-2021.
 */

public class FilePickerActivity extends AppCompatActivity {

    private static final String TAG = "FilePickerActivity";
    public static final String PREF_SORT_BY = "sort_by";
    public static final String PREF_SORT_DESC = "sort_desc";
    public static final Integer SORT_ALPHABETICALLY = 0;
    public static final Integer SORT_DATE = 1;
    public static final Integer SORT_EXIF = 2;
    private UnicornActivityFilePickerBinding filePickerBinding;

    private File root_dir;
    private ArrayList<String> selected_files;
    private ArrayList<DirectoryModel> arr_dir_stack;
    private ArrayList<DirectoryModel> arr_files;

    private DirectoryStackAdapter stackAdapter;
    private DirectoryAdapter directoryAdapter;

    private final String[] REQUIRED_PERMISSIONS = new String[]{
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
    };

    private Config config;
    private ArrayList<String> filters;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = Config.getInstance();
        setTheme(config.getThemeId());
        filePickerBinding = UnicornActivityFilePickerBinding.inflate(getLayoutInflater());
        View view = filePickerBinding.getRoot();
        setContentView(view);

        initConfig();
    }

    private void initConfig() {
        filters = config.getExtensionFilters();


        setSupportActionBar(filePickerBinding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        if (config.getRootDir() != null) {
            root_dir = new File(config.getRootDir());
        } else {
            root_dir = Environment.getExternalStorageDirectory();
        }
        selected_files = new ArrayList<>();
        arr_dir_stack = new ArrayList<>();
        arr_files = new ArrayList<>();

        setUpDirectoryStackView();
        setUpFilesView();

        if (allPermissionsGranted()) {
            fetchDirectory(new DirectoryModel(
                    true,
                    root_dir.getAbsolutePath(),
                    root_dir.getName(),
                    root_dir.lastModified(),
                    root_dir.listFiles() == null ? 0 : root_dir.listFiles().length
            ));
        } else {
            Log.e(TAG, "Storage permissions not granted. You have to implement it before starting the file picker");
            finish();
        }

        filePickerBinding.fabSelect.setOnClickListener((v)->{
            Intent intent = new Intent();
            if(config.showOnlyDirectory()){
                selected_files.clear();
                selected_files.add(arr_dir_stack.get(arr_dir_stack.size()-1).getPath());
            }
            intent.putStringArrayListExtra("filePaths", selected_files);
            setResult(config.getReqCode(), intent);
            setResult(RESULT_OK, intent);
            finish();
        });

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = getTheme();
        theme.resolveAttribute(R.attr.unicorn_fabColor, typedValue, true);
        if(typedValue.data!=0){
            filePickerBinding.fabSelect.setBackgroundTintList(ColorStateList.valueOf(typedValue.data));
        }else{
            filePickerBinding.fabSelect.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.unicorn_colorAccent)));
        }

    }

    private void setUpFilesView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(FilePickerActivity.this);
        filePickerBinding.rvFiles.setLayoutManager(layoutManager);
        directoryAdapter = new DirectoryAdapter(FilePickerActivity.this, arr_files, false, new DirectoryAdapter.onFilesClickListener() {
            @Override
            public void onClicked(DirectoryModel model) {
                fetchDirectory(model);
            }

            @Override
            public void onFileSelected(DirectoryModel fileModel) {
                if(config.isSelectMultiple()){
                    if(selected_files.contains(fileModel.getPath())){
                        selected_files.remove(fileModel.getPath());
                    }else{
                        selected_files.add(fileModel.getPath());
                    }
                }else{
                    selected_files.clear();
                    selected_files.add(fileModel.getPath());
                }
            }
        });
        filePickerBinding.rvFiles.setAdapter(directoryAdapter);
        directoryAdapter.notifyDataSetChanged();
        if(config.addItemDivider()){
            filePickerBinding.rvFiles.addItemDecoration(new UnicornSimpleItemDecoration(FilePickerActivity.this));
        }
    }

    private void setUpDirectoryStackView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(FilePickerActivity.this, RecyclerView.HORIZONTAL, false);
        filePickerBinding.rvDirPath.setLayoutManager(layoutManager);
        stackAdapter = new DirectoryStackAdapter(FilePickerActivity.this, arr_dir_stack, model -> {
            Log.e(TAG, model.toString());
            arr_dir_stack = new ArrayList<>(arr_dir_stack.subList(0, arr_dir_stack.indexOf(model) + 1));
            setUpDirectoryStackView();
            fetchDirectory(arr_dir_stack.remove(arr_dir_stack.size() - 1));
        });

        filePickerBinding.rvDirPath.setAdapter(stackAdapter);
        stackAdapter.notifyDataSetChanged();
    }

    /**
     * Fetches list of files in a folder and filters files if filter present
     */
    private void fetchDirectory(DirectoryModel model) {
        filePickerBinding.rlProgress.setVisibility(View.VISIBLE);
        selected_files.clear();

        arr_files.clear();
        File dir = new File(model.getPath());
        File[] files_list = dir.listFiles();
        if (files_list != null) {
            for (File file : files_list) {
                DirectoryModel directoryModel = new DirectoryModel();
                directoryModel.setDirectory(file.isDirectory());
                directoryModel.setName(file.getName());
                directoryModel.setPath(file.getAbsolutePath());
                directoryModel.setLast_modif_time(file.lastModified());

                if (config.showHidden() || (!config.showHidden() && !file.isHidden())) {
                    if (file.isDirectory()) {
                        if (file.listFiles() != null)
                            directoryModel.setNum_files(file.listFiles().length);
                        arr_files.add(directoryModel);
                    } else {
                        if(!config.showOnlyDirectory()){
                            // Filter out files if filters specified
                            if(filters!=null){
                                try {
                                    // Extract the file extension
                                    String fileName = file.getName();
                                    String extension = fileName.substring(fileName.lastIndexOf("."));
                                    for (String filter : filters) {
                                        if (extension.toLowerCase().contains(filter)) {
                                            arr_files.add(directoryModel);
                                        }
                                    }
                                } catch (Exception e) {
//                                Log.e(TAG, "Encountered a file without an extension: ", e);
                                }
                            }else{
                                arr_files.add(directoryModel);
                            }
                        }
                    }
                }

            }
            Collections.sort(arr_files, new CustomFileComparator(FilePickerActivity.this));

            arr_dir_stack.add(model);
            filePickerBinding.rvDirPath.scrollToPosition(arr_dir_stack.size() - 1);
            filePickerBinding.toolbar.setTitle(model.getName());
        }
        if (arr_files.size() == 0) {
            filePickerBinding.rlNoFiles.setVisibility(View.VISIBLE);
        } else {
            filePickerBinding.rlNoFiles.setVisibility(View.GONE);
        }
        filePickerBinding.rlProgress.setVisibility(View.GONE);
        stackAdapter.notifyDataSetChanged();
        directoryAdapter.notifyDataSetChanged();
    }

    // Custom Comparator to sort the list of files in lexicographical order
    public static class CustomFileComparator implements Comparator<DirectoryModel> {

        Context mContext;
        boolean mDescending;
        public CustomFileComparator(Context context) {
            mContext = context;
            mDescending = Utils.getSharedPreferences(mContext).getBoolean(PREF_SORT_DESC, false);
        }

        @Override
        public int compare(DirectoryModel o1, DirectoryModel o2) {
            if (o1.isDirectory() && o2.isDirectory()) {
                return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
            } else if (o1.isDirectory() && !o2.isDirectory()) {
                return -1;
            } else if (!o1.isDirectory() && o2.isDirectory()) {
                return 1;
            } else {

                int sort_by = Utils.getSharedPreferences(mContext).getInt(PREF_SORT_BY, SORT_ALPHABETICALLY);

                if(sort_by == SORT_DATE)
                {
                    File file1 = new File(o1.getPath());
                    File file2 = new File(o2.getPath());
                    return mDescending ? Long.compare(file2.lastModified(), file1.lastModified()) :   Long.compare(file1.lastModified(), file2.lastModified());
                }
                else if(sort_by == SORT_EXIF)
                {
                    File file1 = new File(o1.getPath());
                    File file2 = new File(o2.getPath());

                    try {
                        ExifInterface exifInterface1 = new ExifInterface(file1);
                        ExifInterface exifInterface2 = new ExifInterface(file2);

                        String dateAttr1 = exifInterface1.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);

                        if (TextUtils.isEmpty(dateAttr1))
                            dateAttr1 = exifInterface1.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED);

                        if (TextUtils.isEmpty(dateAttr1))
                            dateAttr1 = exifInterface1.getAttribute(ExifInterface.TAG_DATETIME);


                        String dateAttr2 = exifInterface2.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);

                        if (TextUtils.isEmpty(dateAttr2))
                            dateAttr2 = exifInterface2.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED);

                        if (TextUtils.isEmpty(dateAttr2))
                            dateAttr2 = exifInterface2.getAttribute(ExifInterface.TAG_DATETIME);


                        if(!TextUtils.isEmpty(dateAttr1) && !TextUtils.isEmpty(dateAttr2))
                        {
                            Date date1 = Utils.getDateFromExifAttribute(dateAttr1);
                            Date date2 = Utils.getDateFromExifAttribute(dateAttr2);
                            return  mDescending ? Long.compare(date2.getTime(),date1.getTime()) : Long.compare(date1.getTime(),date2.getTime());
                        }
                        else if(!TextUtils.isEmpty(dateAttr1) && TextUtils.isEmpty(dateAttr2))
                        {
                          return mDescending ? 1 : -1;
                        }
                        else if(TextUtils.isEmpty(dateAttr1) && !TextUtils.isEmpty(dateAttr2))
                        {
                            return mDescending ? -1 :1;
                        }

                    } catch (Exception e) {
                    }


                }

            }

             return mDescending ? NaturalOrderComparator.s_compare(o2.getName(),o1.getName()) :  NaturalOrderComparator.s_compare(o1.getName(),o2.getName());
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.unicorn_menu_file_picker, menu);

        MenuItem menuSort = menu.findItem(R.id.action_sort);
        MenuItem menuSelect = menu.findItem(R.id.action_select);
        MenuItem menuDeSelect = menu.findItem(R.id.action_unselect);
        menuSort.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(@NonNull MenuItem menuItem) {

                showSortMenu();
                return true;
            }
        });

        menuSelect.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(@NonNull MenuItem menuItem) {
                directoryAdapter.selectAll();
                directoryAdapter.notifyDataSetChanged();
                return true;
            }
        });

        menuDeSelect.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(@NonNull MenuItem menuItem) {
                directoryAdapter.resetSelection();
                directoryAdapter.notifyDataSetChanged();
                return true;
            }
        });
        return true;
    }
	
	 private void showSortMenu() {

        View sortLayout = getLayoutInflater().inflate(R.layout.unicorn_sort_menu, null);

        RadioGroup radioGroup = sortLayout.findViewById(R.id.sort_group);

        CheckBox checkBoxAsc = sortLayout.findViewById(R.id.sort_ascending);

        int sort_by = Utils.getSharedPreferences(this).getInt(PREF_SORT_BY, SORT_ALPHABETICALLY);
        boolean descending = Utils.getSharedPreferences(this).getBoolean(PREF_SORT_DESC, false);

        int checkId = R.id.sort_alphabetically;

        if (sort_by == SORT_DATE)
            checkId = R.id.sort_by_date;
        else if (sort_by == SORT_EXIF)
            checkId = R.id.sort_by_exif;

        radioGroup.check(checkId);
        checkBoxAsc.setChecked(descending);;
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton checkedRadioButton = (RadioButton) group.findViewById(checkedId);
                boolean isChecked = checkedRadioButton.isChecked();

                int sort_by = SORT_ALPHABETICALLY;

                if (isChecked) {
                    if (checkedId == R.id.sort_alphabetically) {
                        sort_by = SORT_ALPHABETICALLY;
                    } else if (checkedId == R.id.sort_by_date) {
                        sort_by = SORT_DATE;
                    } else if (checkedId == R.id.sort_by_exif) {
                        sort_by = SORT_EXIF;
                    }
                    Utils.getSharedPreferences(FilePickerActivity.this).edit().putInt(PREF_SORT_BY, sort_by).apply();
                }
            }
        });
        checkBoxAsc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                Utils.getSharedPreferences(FilePickerActivity.this).edit().putBoolean(PREF_SORT_DESC, checked).apply();
            }
        });


        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setCancelable(false);
        builder.setView(sortLayout);
        builder.setTitle(R.string.unicorn_sort_by);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

     
                selected_files.clear();

                Handler handler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
                    @Override
                    public boolean handleMessage(@NonNull Message message) {

                
                        stackAdapter.notifyDataSetChanged();
                        directoryAdapter.notifyDataSetChanged();
                        return true;
                    }
                });


                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Collections.sort(visible_files, new CustomFileComparator(FilePickerActivity.this));

                        handler.sendEmptyMessage(0);
                    }
                }).start();

            }
        });

        builder.show();

    }

    /**
     * This method checks whether STORAGE permissions are granted or not
     */
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(FilePickerActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (arr_dir_stack.size() > 1) {
            // pop off top value and display
            arr_dir_stack.remove(arr_dir_stack.size() - 1);
            DirectoryModel model = arr_dir_stack.remove(arr_dir_stack.size() - 1);
            fetchDirectory(model);
        } else {
            // Nothing left in stack so exit
            Intent intent = new Intent();
            setResult(config.getReqCode(), intent);
            setResult(RESULT_CANCELED, intent);
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
        }
        return true;
    }
}