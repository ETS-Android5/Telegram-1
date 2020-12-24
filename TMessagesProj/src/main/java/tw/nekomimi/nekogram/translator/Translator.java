package tw.nekomimi.nekogram.translator;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import org.json.JSONException;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;

abstract public class Translator {
    public static final int PROVIDER_GOOGLE = 1;
    public static final int PROVIDER_GOOGLE_CN = 2;
    public static final int PROVIDER_LINGO = 3;
    public static final int PROVIDER_YANDEX = 4;
    public static final int PROVIDER_DEEPL = 5;

    public static void translate(Object query, TranslateCallBack translateCallBack) {
        Locale locale = LocaleController.getInstance().currentLocale;
        String toLang;
        Translator translator;
        switch (NekoConfig.translationProvider) {
            case PROVIDER_YANDEX:
                translator = YandexTranslator.getInstance();
                toLang = locale.getLanguage();
                break;
            case PROVIDER_LINGO:
                toLang = locale.getLanguage();
                translator = LingoTranslator.getInstance();
                break;
            case PROVIDER_DEEPL:
                toLang = locale.getLanguage().toUpperCase();
                translator = DeepLTranslator.getInstance();
                break;
            case PROVIDER_GOOGLE:
            case PROVIDER_GOOGLE_CN:
            default:
                if (locale.getLanguage().equals("zh")) {
                    if (locale.getCountry().toUpperCase().equals("CN") || locale.getCountry().toUpperCase().equals("DUANG")) {
                        toLang = "zh-CN";
                    } else if (locale.getCountry().toUpperCase().equals("TW") || locale.getCountry().toUpperCase().equals("HK")) {
                        toLang = "zh-TW";
                    } else {
                        toLang = locale.getLanguage();
                    }
                } else {
                    toLang = locale.getLanguage();
                }
                translator = GoogleAppTranslator.getInstance();
                break;
        }
        if (!translator.getTargetLanguages().contains(toLang)) {
            translateCallBack.onUnsupported();
        } else {
            translator.startTask(query, toLang, translateCallBack);
        }
    }

    private void startTask(Object query, String toLang, TranslateCallBack translateCallBack) {
        new MyAsyncTask().request(query, toLang, translateCallBack).execute();
    }

    abstract protected String translate(String query, String tl) throws IOException, JSONException;

    abstract protected List<String> getTargetLanguages();

    public interface TranslateCallBack {
        void onSuccess(Object translation);

        void onError(Throwable e);

        void onUnsupported();
    }

    @SuppressLint("StaticFieldLeak")
    private class MyAsyncTask extends AsyncTask<Void, Integer, Object> {
        TranslateCallBack translateCallBack;
        Object query;
        String tl;

        public MyAsyncTask request(Object query, String tl, TranslateCallBack translateCallBack) {
            this.query = query;
            this.tl = tl;
            this.translateCallBack = translateCallBack;
            return this;
        }

        @Override
        protected Object doInBackground(Void... params) {
            try {
                if (query instanceof String) {
                    return translate((String) query, tl);
                } else if (query instanceof TLRPC.Poll) {
                    TLRPC.TL_poll poll = new TLRPC.TL_poll();
                    TLRPC.TL_poll original = (TLRPC.TL_poll) query;
                    poll.question = original.question +
                            "\n" +
                            "--------" +
                            "\n" + translate(original.question, tl);
                    for (int i = 0; i < original.answers.size(); i++) {
                        TLRPC.TL_pollAnswer answer = new TLRPC.TL_pollAnswer();
                        answer.text = original.answers.get(i).text + " | " + translate(original.answers.get(i).text, tl);
                        answer.option = original.answers.get(i).option;
                        poll.answers.add(answer);
                    }
                    poll.close_date = original.close_date;
                    poll.close_period = original.close_period;
                    poll.closed = original.closed;
                    poll.flags = original.flags;
                    poll.id = original.id;
                    poll.multiple_choice = original.multiple_choice;
                    poll.public_voters = original.public_voters;
                    poll.quiz = original.quiz;
                    return poll;
                } else {
                    throw new UnsupportedOperationException("Unsupported translation query");
                }
            } catch (Throwable e) {
                FileLog.e(e);
                return e;
            }
        }

        @Override
        protected void onPostExecute(Object result) {
            if (result == null) {
                translateCallBack.onError(null);
            } else if (result instanceof Throwable) {
                translateCallBack.onError((Throwable) result);
            } else {
                translateCallBack.onSuccess(result);
            }
        }

    }

}
