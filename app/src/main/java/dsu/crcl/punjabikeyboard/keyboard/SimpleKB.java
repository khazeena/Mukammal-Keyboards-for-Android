package dsu.crcl.punjabikeyboard.keyboard;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import dsu.crcl.punjabikeyboard.R;
import dsu.crcl.punjabikeyboard.dictionary.TrieCharacters;
import dsu.crcl.punjabikeyboard.dictionary.TrieWords;
import dsu.crcl.punjabikeyboard.dictionary.WordFreq;
import dsu.crcl.punjabikeyboard.ui.CandidateView;


/**
 * Created by Usama on 7/12/2016.
 */
public class SimpleKB extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView kv;
    private Keyboard keyboard;
    private Keyboard symbols;
    private Keyboard eng_keyboard;

    TrieCharacters trieCharacters;
    TrieWords trieWords;
    String previousWord;

    Context context  = this;
    boolean characterReady = false;
    boolean wordsReady = false;

    private boolean mPredictionOn =true;
    private boolean mCompletionOn = false;
    private boolean mCapsLock;
    private StringBuilder mComposing = new StringBuilder();
    CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;
    private String mWordSeparators;
    private String uWordSeparators;
    private int mLastDisplayWidth;
    private long mLastShiftTime;

    public String getuWordSeparators() {
        return uWordSeparators;
    }

    private boolean caps = false;
    private List<String> fromDictionary;
    private List<String> nextWord;


    //Core overridden Functions
    @Override public View onCreateInputView() {
        kv = (KeyboardView)getLayoutInflater().inflate(R.layout.keyboard, null);
        keyboard = new Keyboard(this, R.xml.qwerty);
        symbols = new Keyboard(this, R.xml.symbol);
        eng_keyboard = new Keyboard(this, R.xml.eng_qwerty);
        kv.setKeyboard(keyboard);
        kv.setOnKeyboardActionListener(this);
        return kv;
    }

    @Override public void onInitializeInterface() {
        if (keyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }
        keyboard = new Keyboard(this, R.xml.qwerty);
    }

    @Override public void onCreate() {
        super.onCreate();
        mWordSeparators = getResources().getString(R.string.word_separators);
        uWordSeparators = getResources().getString(R.string.urdu_separators);

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground( Void... voids ) {
                trieCharacters = new TrieCharacters();
                try {
                    Log.e("Unigram Reading", "start");
                    InputStream inputStream = context.getResources().openRawResource(R.raw.unigrampunjabi);

                    InputStreamReader inputreader = new InputStreamReader(inputStream);
                    BufferedReader buff = new BufferedReader(inputreader);
                    String line;

                    while ((line = buff.readLine()) != null) {
                        trieCharacters.insert(line);
                    }

                }

                catch (IOException iex) {
                    Logger.getLogger(SimpleKB.class.getName()).log(Level.SEVERE, null, iex);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                Log.e("Unigram", "ready");
                characterReady = true;
            }

        };
        task.execute();

        final AsyncTask <Void, Void, Void> task1 = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground( Void... voids ) {
                trieWords = new TrieWords();
                try {
                    Log.e("Biagram Reading", "start");
                    InputStream inputStream = context.getResources().openRawResource(R.raw.bigrampunjabi);

                    InputStreamReader inputreader = new InputStreamReader(inputStream);
                    BufferedReader buff = new BufferedReader(inputreader);
                    String line;

                    while ((line = buff.readLine()) != null) {
                        try {
                            trieWords.insert(line);
                        }
                        catch (Exception e){

                        }
                    }

                }

                catch (IOException iex) {
                    Logger.getLogger(SimpleKB.class.getName()).log(Level.SEVERE, null, iex);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                Log.e("Biagram", "ready");
                characterReady = true;
            }

        };
        task1.execute();


    }

    @Override public View onCreateCandidatesView() {
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        return mCandidateView;
    }

    @Override public void onKey(int primaryCode, int[] keyCodes) {
        previousWord="";
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
                previousWord=mComposing.toString();
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
        }
        playClick(primaryCode);
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();

        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_DONE) {
            handleClose();
        }
        else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            Keyboard current = kv.getKeyboard();
            if (current == symbols) {
                current = keyboard;
            } else {
                current = symbols;
            }
            kv.setKeyboard(current);
            if (current == symbols) {
                current.setShifted(false);
            }
        }
        else if (primaryCode == -6) {
            Keyboard current = kv.getKeyboard();
            if (current == eng_keyboard) {
                current = keyboard;
            } else {
                current = eng_keyboard;
            }
            kv.setKeyboard(current);
        }
        else {
            handleCharacter(primaryCode, keyCodes);

        }
    }

    //general Overridden Fuctions
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }

            List<String> stringList = new ArrayList<String>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && kv != null) {
                    if (kv.handleBack()) {
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;

            default:
                return false;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        Keyboard current;
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();


        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;

        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                current = symbols;
                break;

            case EditorInfo.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                current = symbols;
                break;

            case EditorInfo.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                current = symbols;
                mPredictionOn = true;

                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    mPredictionOn = false;
                }

                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_URI
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    mPredictionOn = false;
                }

                if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
                }

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                current = keyboard;
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
    }


    @Override public void onFinishInputView(boolean finishingInput) {
        if (!finishingInput) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                previousWord = "";
                nextWord = new ArrayList<>();
                fromDictionary = new ArrayList<>();
                mComposing.setLength(0);
                updateCandidates();
                ic.finishComposingText();
                handleClose();
            }
        }
    }
    //Small Overridden Functions
    @Override
    public void onPress(int primaryCode) {
    }

    @Override
    public void onRelease(int primaryCode) {
    }

    @Override
    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        Log.v("text", text.toString());
        ic.commitText(text, 0);
        ic.endBatchEdit();
    }

    @Override
    public void swipeDown() {
        handleClose();
    }

    @Override
    public void swipeLeft() {
        pickSuggestionManually(1);
    }

    @Override
    public void swipeRight() {
        handleBackspace();
    }

    @Override
    public void swipeUp() {
    }


    //Handling functions
    private void handleClose() {
        requestHideSelf(0);
        mComposing = new StringBuilder();
        setSuggestions(null, false, false);
        updateCandidates();
        kv.closing();
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (isInputViewShown()) {
            if (isInputViewShown()) {
                if (kv.isShifted()) {
                    primaryCode = Character.toUpperCase(primaryCode);
                }
            }
        }

        if (isAlphabet(primaryCode) && mPredictionOn) {
            mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        }
    }

    private void handleShift() {
        if (kv == null) {
            return;
        }

        Keyboard currentKeyboard = kv.getKeyboard();
        if (eng_keyboard == currentKeyboard) {
            // Alphabet keyboard
            checkToggleCapsLock();
            kv.setShifted(mCapsLock || !kv.isShifted());
        }/* else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            mInputView.setKeyboard(mSymbolsShiftedKeyboard);
            mSymbolsShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        }*/
    }

    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }


    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null && kv != null && eng_keyboard == kv.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            kv.setShifted(mCapsLock || caps != 0);
        }
    }

    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            setSuggestions(null, false, false);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            setSuggestions(null, false, false);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();


        }else if (length == 0) {

            mComposing.setLength(0);
            setSuggestions(null, false, false);
            try{
                checkForCompose();
            }
            catch(Exception e){
                Log.v("Empty", "nothing to get");
                keyDownUp(KeyEvent.KEYCODE_DEL);
            }

            updateCandidates();
        }else{
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
    }

    private void checkForCompose() {
        String backWord = getTextBeforeCursor();
        Log.v("String", backWord);
        mComposing.setLength(0);
        mComposing.append(backWord);
        getCurrentInputConnection().setComposingText(backWord, 1);

    }

    public String getTextBeforeCursor() {
        // TODO: use mCommittedTextBeforeComposingText if possible to improve performance
        if (null != getCurrentInputConnection())
        {
            CharSequence sentence = getCurrentInputConnection().getTextBeforeCursor(100, 0);

            if (sentence.charAt(sentence.length() - 1) == ' ') {
                sentence = sentence.subSequence(0, sentence.length() - 1);
                getCurrentInputConnection().deleteSurroundingText(1, 0);
            }
            String[] returner = sentence.toString().split(" ");
            String word = returner[returner.length - 1];
            Log.e("Word", word);
            getCurrentInputConnection().deleteSurroundingText(word.length(), 0);

            return word;

        }
        return null;
    }


    private void playClick(int keyCode){
        AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        switch(keyCode){
            case 32:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR);
                break;
            case Keyboard.KEYCODE_DONE:
            case 10:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN);
                break;
            case Keyboard.KEYCODE_DELETE:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE);
                break;
            default: am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
        }
    }

    private String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators()+getuWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    private void sendKey(int keyCode) {
        if (keyCode == '\n') {
            keyDownUp(KeyEvent.KEYCODE_ENTER);

        } else {
            if (keyCode >= '0' && keyCode <= '9') {
                keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
            } else {
                getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
            }

        }
    }

    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    private void updateCandidates() {

        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
                ArrayList<String> list = new ArrayList<String>();
                list.add(mComposing.toString());
                setSuggestions(list, true, true);
            } else if(previousWord!=null){
                if(!previousWord.equals("")) {
                    ArrayList<String> list = new ArrayList<String>();
                    list.add(previousWord.trim());
                    setSuggestions(list, true, true);
                }
            }else{
                setSuggestions(null, false, false);
            }
        }
    }

    public void setSuggestions(List<String> suggestions, boolean completions,
                               boolean typedWordValid) {

        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null) {
            try {
                suggestions = getFromDictionary(suggestions.get(0));
                if(!previousWord.equals(""))
                    suggestions = getNextWord(previousWord);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }

            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }

    public void pickSuggestionManually(int index) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            //updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0) {
            mComposing = new StringBuilder();
            mComposing.append(fromDictionary.get(index)+" ");

            commitTyped(getCurrentInputConnection());
        }else if(!previousWord.equals("")){
            try {
                mComposing = new StringBuilder();
                mComposing.append(nextWord.get(index) + " ");
                previousWord = mComposing.toString().trim();
                commitTyped(getCurrentInputConnection());
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            previousWord = mComposing.toString().trim();
            inputConnection.commitText(mComposing, mComposing.length());

            mComposing.setLength(0);
            updateCandidates();
        }
    }

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }


    //Populating predictions

    public List<String> getFromDictionary(String string) {

        fromDictionary = new ArrayList<>();
        Collection<String> list = trieCharacters.autoComplete(string);
        if(!list.isEmpty()){
            List<String> wordList = new ArrayList(list);
            List<WordFreq> sortedList = new ArrayList();
            for (String wordList1 : wordList) {

                String[] tokens = wordList1.split(" ");
                WordFreq wordFreq = new WordFreq(tokens[0], Integer.parseInt(tokens[1]));
                sortedList.add(wordFreq);
            }
            Collections.sort(sortedList);
            int noOfSuggestions = 6;
            if(sortedList.size()<noOfSuggestions)
            {
                noOfSuggestions = sortedList.size();
            }
            fromDictionary.add(string);
            for(int i=0; i<noOfSuggestions; i++)
            {
                WordFreq w = sortedList.get(i);
                fromDictionary.add(w.getLetter());
            }
        }
        else
        {
            Log.v("No Suggestion","Empty String");
        }
        return fromDictionary;
    }

    public List<String> getNextWord(String string) {

        nextWord = new ArrayList<>();
        Collection<String> list = trieWords.autoComplete(string);
        if(!list.isEmpty()){
            List<String> wordList = new ArrayList(list);
            List<WordFreq> sortedList = new ArrayList();
            for (String wordList1 : wordList) {

                String[] tokens = wordList1.split(" ");
                WordFreq wordFreq = new WordFreq(tokens[0], Integer.parseInt(tokens[1]));
                sortedList.add(wordFreq);
            }
            Collections.sort(sortedList);
            int noOfSuggestions = 6;
            if(sortedList.size()<noOfSuggestions)
            {
                noOfSuggestions = sortedList.size();
            }
            for(int i=0; i<noOfSuggestions; i++)
            {
                WordFreq w = sortedList.get(i);
                String[] next = w.getLetter().split("_");
                try {
                    nextWord.add(next[1]);
                }
                catch(ArrayIndexOutOfBoundsException e)
                {

                }

            }

        }
        return nextWord;
    }
}