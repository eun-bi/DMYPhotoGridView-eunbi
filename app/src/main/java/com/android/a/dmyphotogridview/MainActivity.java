package com.android.a.dmyphotogridview;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.media.ExifInterface;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifImageDirectory;
import com.tonicartos.widget.stickygridheaders.StickyGridHeadersSimpleAdapter;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final int MY_PERMISSION_STORAGE = 123;

    private GridView grid_gallery;
    private Button btn3, btn5, btn7;

    private ImageAdapter imageAdapter = null;

    private int count;
    private String[] arrPath; // image path 배열
    private int ids[];

    private String date; // exif에서 얻어온 날짜 정보

    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    int mode = NONE;

    //드래그시 좌표 저장
    int posX1 = 0, posX2 = 0, posY1 = 0, posY2 = 0;

    // multitouch 의 경우 좌표 저장
    private float oldDist = 1f;
    private float newDist = 1f;

    ArrayList<GalleryImgs> galleryImgses = new ArrayList<GalleryImgs>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView(); // 초기화
        setEvent(); // 이벤트 처리
    }

    @Override
    protected void onResume() {
        checkPermission(); // 권한 check
        super.onResume();
    }

    /* multi touch pinch zoom을 위한 이벤트*/
    public void pinchInOut(MotionEvent event){

        int act = event.getAction();

        switch (act & MotionEvent.ACTION_MASK){
            case MotionEvent.ACTION_DOWN : // 첫 번째 손가락 터치
                posX1 = (int) event.getX();
                posY1 = (int) event.getY();
                mode = DRAG;
                break;

            case MotionEvent.ACTION_MOVE:
                if(mode == DRAG) { // drag
                    posX2 = (int) event.getX();
                    posY2 = (int) event.getY();
                    if (Math.abs(posX2 - posX1) > 20 || Math.abs(posY2 - posY1) > 20) {
                        posX1 = posX2;
                        posY1 = posY2;
                    }
                }
                else if(mode == ZOOM){ // pinch zoom
                    newDist = spacing(event);
                    Log.d("zoom", "newDist=" + newDist);
                    Log.d("zoom", "oldDist=" + oldDist);
                    if (newDist - oldDist > 700) { // zoom in
                        oldDist = newDist;
                        grid_gallery.setNumColumns(5); // test
                        //todo zoom in 되었을 때 구현
                    } else if(oldDist - newDist > 700) { // zoom out
                        oldDist = newDist;
                        grid_gallery.setNumColumns(7); // test
                        //todo zoom out 되었을 때 구현
                    }
                }
                break;
            case MotionEvent.ACTION_UP: // 첫번째 손가락 떼었을 경우
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;

            case MotionEvent.ACTION_POINTER_DOWN: //두번째 손가락 터치

                mode = ZOOM;

                newDist = spacing(event);
                oldDist = spacing(event);

                break;
            case MotionEvent.ACTION_CANCEL:
            default:
                break;
        }
    }

    private float spacing(MotionEvent event){
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float)Math.sqrt(x*x + y*y);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        pinchInOut(event);
        return super.dispatchTouchEvent(event);
    }

    private void getImgs() {

        final String[] columns = {MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID};
        final String orderBy = MediaStore.Images.Media.DATE_TAKEN;

        Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null, orderBy + " DESC" + " limit 1000");
        int image_column_index = cursor.getColumnIndex(MediaStore.Images.Media._ID);
        this.count = cursor.getCount();
        this.arrPath = new String[this.count];

        ids = new int[count];

        for (int i=0; i<this.count;i++){
            cursor.moveToPosition(i);
            ids[i] = cursor.getInt(image_column_index);
            int datacolumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
            arrPath[i] = cursor.getString(datacolumnIndex);

            Log.d("img_path", arrPath[i]);

            date = extractExifDateTime(arrPath[i]);
            if (date != null){
                Log.d("img_date", date);
            }else{
                Log.d("img_date", "null");
            }

            GalleryImgs imgs = new GalleryImgs();
            imgs.setImg_url(arrPath[i]);
            imgs.setTime(date);
            galleryImgses.add(imgs);
        }

        cursor.close();

        imageAdapter = new ImageAdapter(this, R.layout.item_img, galleryImgses);
        grid_gallery.setAdapter(imageAdapter);

    }

    /* 사진 exif 정보 받아오기*/
    private String extractExifDateTime(String imagePath) {
        Log.d("exif", "Attempting to extract EXIF date/time from image at " + imagePath);
        Date datetime = new Date(0); // or initialize to null, if you prefer
        String formatdate = " ";
        try {
            Metadata metadata = JpegMetadataReader.readMetadata(new File(imagePath));
            // these are listed in order of preference
            int[] datetimeTags = new int[] { ExifImageDirectory.TAG_DATETIME_ORIGINAL,
                    ExifImageDirectory.TAG_DATETIME,
                    ExifImageDirectory.TAG_DATETIME_DIGITIZED };

            for (Directory directory : metadata.getDirectories()) {
                for (int tag : datetimeTags) {
                    if (directory.containsTag(tag)) {
                        Log.d("exif", "Using tag " + directory.getTagName(tag) + " for timestamp");
                        SimpleDateFormat exifDatetimeFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault());
                        datetime = exifDatetimeFormat.parse(directory.getString(tag));
                        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                        formatdate = format.format(datetime);

                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.w("exif", "Unable to extract EXIF metadata from image at " + imagePath, e);
        }

        return formatdate;
    }

    public class ImageAdapter extends ArrayAdapter<GalleryImgs> implements StickyGridHeadersSimpleAdapter{

        private LayoutInflater layoutInflater = null;
        private ArrayList<GalleryImgs> galleryImgsArrayList = null;
        private GridView mGridView;

        /* 생성자 */
        public ImageAdapter(@NonNull Context context, int textViewResourceId, @NonNull ArrayList<GalleryImgs> imgses) {
            super(context, textViewResourceId, imgses);

            layoutInflater = LayoutInflater.from(context);
            this.galleryImgsArrayList = new ArrayList<>();
            this.galleryImgsArrayList.addAll(imgses);
        }


        @Override
        public int getCount() {
            return count;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

            final ImageViewHolder imageViewHolder;

            if(convertView == null){
                convertView = layoutInflater.inflate(R.layout.item_img, parent, false);
                imageViewHolder = new ImageViewHolder();
                imageViewHolder.img_gallery = (ImageView)convertView.findViewById(R.id.img_gallery);
                convertView.setTag(imageViewHolder);
            }else{
                imageViewHolder = (ImageViewHolder)convertView.getTag();
            }

            imageViewHolder.img_gallery.setId(position);

            GalleryImgs img = this.galleryImgsArrayList.get(position);

            Glide
                    .with(getApplicationContext())
                    .load(img.getImg_url())
                    .fitCenter()
                    .centerCrop()
                    .into(imageViewHolder.img_gallery);


            imageViewHolder.img_gallery.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //todo 사진 클릭 시 전체 화면 구현
                }
            });

            imageViewHolder.id = position;

            return convertView;
        }

        @Override
        public long getHeaderId(int position) {
            return galleryImgsArrayList.get(position).getHeaderId();
        }

        @Override
        public View getHeaderView(int position, View convertView, ViewGroup parent) {

            final HeaderViewHolder headerViewHolder;

            if(convertView == null){
                convertView = layoutInflater.inflate(R.layout.item_header, parent, false);
                headerViewHolder = new HeaderViewHolder();
                headerViewHolder.txtHeader = (TextView)convertView.findViewById(R.id.txtHeader);
                convertView.setTag(headerViewHolder);
            }else{
                headerViewHolder = (HeaderViewHolder)convertView.getTag();
            }

            headerViewHolder.txtHeader.setText(galleryImgsArrayList.get(position).getTime());

            return convertView;
        }


        /* viewholder 생성 */
        class ImageViewHolder{
            int id;
            ImageView img_gallery;
        }

        class HeaderViewHolder{
            TextView txtHeader;
        }
    }

    private void setEvent() {


        /* gridview row 확인을 위한 test */
        btn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                grid_gallery.setNumColumns(3);
            }
        });

        btn5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                grid_gallery.setNumColumns(5);
            }
        });

        btn7.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                grid_gallery.setNumColumns(7);
            }
        });




    }

    private void initView() {
        grid_gallery = (GridView)findViewById(R.id.grid_gallery);
        btn3 = (Button)findViewById(R.id.btn3);
        btn5 = (Button)findViewById(R.id.btn5);
        btn7 = (Button)findViewById(R.id.btn7);
    }

    private boolean checkPermission() {

        int currentAPIVersion = Build.VERSION.SDK_INT;
        if(currentAPIVersion >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_EXTERNAL_STORAGE)){
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setTitle("Permission necessary");
                    builder.setMessage("Write storage permission is necessary to write picture!!!");
                    builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSION_STORAGE);
                        }
                    });
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();

                }else{
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSION_STORAGE);
                }
                return false;
            }else{
                getImgs();
                return true;
            }
        }else{
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        switch (requestCode){
            case MY_PERMISSION_STORAGE :
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    getImgs();
                }
                break;
        }
    }


}
