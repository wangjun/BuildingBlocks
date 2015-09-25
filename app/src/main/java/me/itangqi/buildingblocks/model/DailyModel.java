package me.itangqi.buildingblocks.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.BaseJsonHttpResponseHandler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.parser.XmlTreeBuilder;
import org.jsoup.select.Elements;

import org.apache.http.Header;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import me.itangqi.buildingblocks.domin.api.ZhihuApi;
import me.itangqi.buildingblocks.domin.application.App;
import me.itangqi.buildingblocks.domin.db.SQLiteHelper;
import me.itangqi.buildingblocks.domin.utils.CommonUtils;
import me.itangqi.buildingblocks.domin.utils.NetworkUtils;
import me.itangqi.buildingblocks.domin.utils.PrefUtils;
import me.itangqi.buildingblocks.model.entity.Daily;
import me.itangqi.buildingblocks.model.entity.DailyGson;
import me.itangqi.buildingblocks.model.entity.DailyResult;
import me.itangqi.buildingblocks.model.entity.Theme;

/**
 * Created by Troy on 2015/9/21.
 */
public class DailyModel implements IDaily {

    private List<Daily> mDailiesFromNet;
    private List<Daily> mDailiesFromCache;
    private IHttpCallBack mIHttpCallBack;
    private IGsonCallBack mIGsonCallBack;
    //    private SparseArray<String> mItems = new SparseArray<>();  //待测试
    private SQLiteHelper mSQLiteHelper;
    private static DailyModel mDailyModel;

    //用来获取Daily集合
    AsyncHttpResponseHandler mAsyncHttpResponseHandler = new BaseJsonHttpResponseHandler<DailyResult>() {
        @Override
        public void onSuccess(int statusCode, Header[] headers, String rawJsonResponse, DailyResult response) {
            if (response != null && response.stories.size() != 0) {
                for (Daily daily : response.stories) {
                    mDailiesFromNet.add(daily);
//                    mItems.put(daily.id, daily.title);   //待测试
                }
                mIHttpCallBack.onFinish(mDailiesFromNet);
                saveDailyStoriesDB(response);
            }
        }

        @Override
        public void onFailure(int statusCode, Header[] headers, Throwable throwable, String rawJsonData, DailyResult errorResponse) {

        }

        @Override
        protected DailyResult parseResponse(String rawJsonData, boolean isFailure) throws Throwable {
            Gson gson = new Gson();
            return gson.fromJson(rawJsonData, DailyResult.class);
        }
    };

    // 用来获取DailyGson的数据
    AsyncHttpResponseHandler mGsonNewsResponseHandler = new BaseJsonHttpResponseHandler<DailyGson>() {
        @Override
        public void onSuccess(int statusCode, Header[] headers, String rawJsonResponse, DailyGson response) {
            mIGsonCallBack.onGsonItemFinish(response);
            saveDailyGsonDB(response);
        }

        @Override
        public void onFailure(int statusCode, Header[] headers, Throwable throwable, String rawJsonData, DailyGson errorResponse) {

        }

        @Override
        protected DailyGson parseResponse(String rawJsonData, boolean isFailure) throws Throwable {
            Gson gson = new Gson();
            return gson.fromJson(rawJsonData, DailyGson.class);
        }
    };

    public static DailyModel newInstance(IHttpCallBack iHttpCallBack) {
        if (mDailyModel == null) {
            return new DailyModel(iHttpCallBack);
        } else {
            return mDailyModel;
        }
    }

    public static DailyModel newInstance(IGsonCallBack iGsonCallBack) {
        if (mDailyModel == null) {
            return new DailyModel(iGsonCallBack);
        } else {
            return mDailyModel;
        }
    }

    public static DailyModel newInstance() {
        if (mDailyModel == null) {
            return new DailyModel();
        } else {
            return mDailyModel;
        }
    }

    private DailyModel() {
        mSQLiteHelper = new SQLiteHelper();
    }

    private DailyModel(IHttpCallBack IHttpCallBack) {
        this();
        this.mIHttpCallBack = IHttpCallBack;
        this.mDailiesFromNet = new ArrayList<>();
        this.mDailiesFromCache = new ArrayList<>();
    }

    private DailyModel(IGsonCallBack IGsonCallBack) {
        this();
        this.mIGsonCallBack = IGsonCallBack;
        this.mDailiesFromNet = new ArrayList<>();
        this.mDailiesFromCache = new ArrayList<>();
    }

    public void getDailyResult(int date) {
        if (PrefUtils.isEnableCache() && NetworkUtils.isNetworkConnected()) {
            getFromCache(date);
            getFromNet(date);
        } else if (!PrefUtils.isEnableCache() && NetworkUtils.isNetworkConnected()) {
            getFromNet(date);
        } else if (!NetworkUtils.isNetworkConnected()) {
            getFromCache(date);
        }
    }

    private void getFromNet(int date) {
        String url = ZhihuApi.getDailyNews(date);
        AsyncHttpClient client = new AsyncHttpClient();
        client.get(url, mAsyncHttpResponseHandler);
    }

    private void getFromCache(int date) {
        getDailyStoriesDB(date);
    }


    @Override
    public void saveDailies(List<Daily> dailies, int date) {
        try {
            serializDaily(date, dailies);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void getGsonNews(int id) {
        if (getDailyGsonDB(id) == null) {
            String url = ZhihuApi.getGsonNewsContent(id);
            AsyncHttpClient client = new AsyncHttpClient();
            client.get(url, mGsonNewsResponseHandler);
        }
    }


    //    "id smallint primary key,"
//            "title text,"
//            "image_source text,"
//            "image text,"
//            "share_url text,"
//            "ga_prefix int,"
//            "body text)";
    @Deprecated
    private void saveDailyGsonDB(DailyGson daily) {
        SQLiteDatabase database = mSQLiteHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", daily.getId());
        values.put("title", daily.getTitle());
        values.put("type", daily.getTitle());
        values.put("image_source", daily.getImage_source());
        values.put("image", daily.getImage());
        values.put("share_url", daily.getShare_url());
        values.put("ga_prefix", daily.getGa_Prefix());
        values.put("body", daily.getBody());
        database.insertWithOnConflict("daily", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        database.close();
    }

    private void insertDailyGsonDB(DailyGson dailyGson) {
        SQLiteDatabase database = mSQLiteHelper.getWritableDatabase();
        String sql = "INSERT OR IGNORE INTO daily(id, title, image_source, image, share_url, ga_prefix, body) values("
                + dailyGson.getId() + ","
                + "\"" + dailyGson.getTitle() + "\"" + ","
                + "\"" + dailyGson.getImage_source() + "\"" + ","
                + "\"" + dailyGson.getImage() + "\"" + ","
                + "\"" + dailyGson.getShare_url() + "\"" + ","
                + dailyGson.getGa_Prefix() + ","
                + "\"" + dailyGson.getBody() + "\"" + ")";
        database.execSQL(sql);
        database.close();
    }

    private DailyGson getDailyGsonDB(int id) {
        SQLiteDatabase database = mSQLiteHelper.getReadableDatabase();
        Cursor cursor = database.rawQuery("SELECT * FROM daily where id = ?", new String[]{id + ""});
        DailyGson dailyGson = new DailyGson();
        if (cursor.moveToFirst()) {
            dailyGson.setId(cursor.getInt(cursor.getColumnIndex("id")));
            dailyGson.setTitle(cursor.getString(cursor.getColumnIndex("title")));
            dailyGson.setType(cursor.getInt(cursor.getColumnIndex("type")));
            dailyGson.setImage_source(cursor.getString(cursor.getColumnIndex("image_source")));
            dailyGson.setImage(cursor.getString(cursor.getColumnIndex("image")));
            dailyGson.setShare_url(cursor.getString(cursor.getColumnIndex("share_url")));
            dailyGson.setGa_prefix(cursor.getInt(cursor.getColumnIndex("ga_prefix")));
            dailyGson.setBody(cursor.getString(cursor.getColumnIndex("body")));
            cursor.close();
            mIGsonCallBack.onGsonItemFinish(dailyGson);
            database.close();
            return dailyGson;
        }
        database.close();
        return null;
    }

    @Deprecated
    private void saveDailyStoriesDB(DailyResult dailyResult) {
        SQLiteDatabase database = mSQLiteHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        for (Daily story : dailyResult.stories) {
            values.put("date", dailyResult.date);
            values.put("id", story.id);
            values.put("title", story.title);
            values.put("image", story.images.get(0));
            values.put("type", story.type);
            values.put("ga_prefix", story.ga_prefix);
            database.insertWithOnConflict("dailyresult", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
        database.close();
    }

    //    + "date SMALLINT,"
//            + "id SMALLINT PRIMARY KEY,"
//            + "title TEXT,"
//            + "image TEXT NULL,"
//            + "type SMALLINT,"
//            + "ga_prefix SMALLINT)";
    private void insertDailyStoriesDB(DailyResult dailyResult) {
        SQLiteDatabase database = mSQLiteHelper.getWritableDatabase();
        for (Daily story : dailyResult.stories) {
            String sql = "INSERT OR IGNORE INTO dailyresult(date, id, title, image, type, ga_prefix) values("
                    + dailyResult.date + ","
                    + story.id + ","
                    + "\"" + story.title + "\"" + ","
                    + "\"" + story.images.get(0) + "\"" + ","
                    + story.type + ","
                    + story.ga_prefix + ")";
            database.execSQL(sql);
        }
        database.close();
    }

    private void getDailyStoriesDB(int date) {
        SQLiteDatabase database = mSQLiteHelper.getWritableDatabase();
        List<Daily> dailyList = new ArrayList<>();
        Cursor cursor = database.rawQuery("SELECT * FROM dailyresult where date = ?", new String[]{date + ""});
        while (cursor.moveToNext()) {
            Daily daily = new Daily();
            daily.id = cursor.getInt(cursor.getColumnIndex("id"));
            daily.title = cursor.getString(cursor.getColumnIndex("title"));
            daily.image = cursor.getString(cursor.getColumnIndex("image"));
            daily.type = cursor.getInt(cursor.getColumnIndex("type"));
            daily.ga_prefix = cursor.getInt(cursor.getColumnIndex("ga_prefix"));
            dailyList.add(daily);
        }
        mIHttpCallBack.onFinish(dailyList);
        database.close();
    }

    /**
     * 使用Jsoup来对数据库里面的DaliyGson中的body字段进行解析
     *
     * @param dailyGson
     * @return 返回一个包含额外信息和正文的HashMap
     */
    public Map<String, LinkedHashMap<String, String>> parseBody(DailyGson dailyGson) {
        long before = System.currentTimeMillis();
        String xml = dailyGson.getBody();
        Map<String, LinkedHashMap<String, String>> soup = new HashMap<String, LinkedHashMap<String, String>>();
        LinkedHashMap<String, String> extra = new LinkedHashMap<String, String>();
        LinkedHashMap<String, String> article = new LinkedHashMap<String, String>();
        Document document = Jsoup.parse(xml, "", new Parser(new XmlTreeBuilder()));
        Elements all = document.getAllElements();
        Log.i("Parsing", "all.size--->" + all.size());
//		Element content_innner = document.select("div[class=\\\"content-inner\\\"]").get(0);
//		Element h2 = document.select("h2[class=\\\"question-title\\\"").get(0);
//		System.out.println("h2:--->" + h2.text());
//		Element author = document.select("span[class=\\\"author\\\"").get(0);
//		System.out.println("author:--->"+author.text());
//		Elements contents = content_innner.getAllElements();
//		System.out.println(contents.size());
        for (Element content : all) {
            if (content.hasClass("avatar")) {
                String src = content.attr("src");
                extra.put("avatar", src);
                Log.i("parsing", "avatar--->" + src);
            } else if (content.hasClass("author")) {
                extra.put("author", content.text());
                Log.i("parsing", "author--->" + content.text());
            } else if (content.hasClass("bio")) {
                extra.put("bio", content.text());
                Log.i("parsing", "bio--->" + content.text());
            } else if (content.hasClass("content")) {
                for (Element item : content.getAllElements()) {
                    if (item.nodeName().equals("p") && item.getAllElements().size() == 1) {
                        article.put(item.text(), "p");
                    } else if (item.nodeName().equals("img")) {
                        String src = item.attr("src");
                        article.put(src, "img");
                    } else if (item.nodeName().equals("strong")) {
                        article.put(item.text(), "strong");
                    } else if (item.nodeName().equals("blockquote")) {
                        article.put(item.text(), "blockquote");
                    }
                }
            }
        }
        soup.put("extra", extra);
        soup.put("article", article);
        long after = System.currentTimeMillis();
        Log.d("Parsing XML", "used time--->" + (after - before));
        return soup;
    }

    @Deprecated
    private void serializDaily(int date, List<Daily> dailyList) throws IOException {
        String itemParentPath = App.getContext().getCacheDir().getAbsolutePath() + "/daily";
        Log.i("itemPath", itemParentPath);
        File itemParent = new File(itemParentPath);
        if (!itemParent.exists()) {
            boolean createdDir = itemParent.mkdir();
            Log.d("serilizDaily", itemParent.getName() + (createdDir ? "创建成功" : "创建失败"));
        }
        File item = new File(itemParentPath + "/" + date);
        FileOutputStream fos = new FileOutputStream(item);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(dailyList);
        oos.close();
    }

    @Deprecated
    private List<Daily> deserializDaily(int date) throws IOException, ClassNotFoundException {
        String cachePath = App.getContext().getCacheDir().getAbsolutePath();
        FileInputStream fis = new FileInputStream(cachePath + "/daily/" + date);
        ObjectInputStream ois = new ObjectInputStream(fis);
        List<Daily> dailyList = (List<Daily>) ois.readObject();
        ois.close();
        return dailyList;
    }

    @Deprecated
    private boolean hasSerializedObject(int date) {
        String cachePath = App.getContext().getCacheDir().getAbsolutePath();
        File file = new File(cachePath + "/daily/" + date);
        return file.exists();
    }

}