package com.android.a.dmyphotogridview;

import android.Manifest;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    public static final int MY_PERMISSION_STORAGE = 123;

    private GridView grid_gallery;

    private ImageAdapter imageAdapter = null;

    private int count;
    private String[] arrPath; // image path 배열
    private int ids[];

    ArrayList<GalleryImgs> galleryImgses = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission(); // 권한 check

        initView(); // 초기화
        setEvent(); // 이벤트 처리
    }



    private void getImgs() {

        final String[] columns = {MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID};
        final String orderBy = MediaStore.Images.Media.DATE_TAKEN;

        Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null, orderBy + " DESC");
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

//            try{
//                ExifInterface exif = new ExifInterface(arrPath[i]);
//                String date = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME);
//                Log.d("img_date", date);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            GalleryImgs imgs = new GalleryImgs();
            imgs.setImg_url(arrPath[i]);
            galleryImgses.add(imgs);
        }

        cursor.close();

        imageAdapter = new ImageAdapter(this, R.layout.item_img, galleryImgses);
        grid_gallery.setAdapter(imageAdapter);

    }

    public class ImageAdapter extends ArrayAdapter<GalleryImgs>{

        private LayoutInflater layoutInflater = null;
        private ArrayList<GalleryImgs> galleryImgsArrayList = null;

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
                convertView = layoutInflater.inflate(R.layout.item_img, null);
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


        /* viewholder 생성 */
        class ImageViewHolder{

            int id;
            ImageView img_gallery;

        }
    }

    private void setEvent() {

    }

    private void initView() {
        grid_gallery = (GridView)findViewById(R.id.grid_gallery);
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
