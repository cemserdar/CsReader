import AsyncStorage from '@react-native-async-storage/async-storage';
import * as FileSystem from 'expo-file-system/legacy';

export interface Book {
  id: string;
  title: string;
  author: string;
  uri: string;
  type: 'epub' | 'pdf';
  progress: number; // 0 to 1
  lastCfi?: string; // For EPUB
  lastPage?: number; // For PDF
  lastRead: number; // timestamp
  addedDate: number; // timestamp
  favorite: boolean;
}

export interface Highlight {
  id: string;
  bookId: string;
  cfiRange?: string; // For EPUB
  page?: number; // For PDF
  text: string;
  note?: string;
  color: 'yellow' | 'green' | 'pink' | 'blue' | 'underline';
  date: number;
}

export type PageTransition = 'none' | 'slide' | 'fade';

export interface Settings {
  theme: 'light' | 'dark' | 'sepia' | 'forest';
  fontSize: number;
  pageTransition: PageTransition;
}

const BOOKS_KEY = '@csreader_books';
const SETTINGS_KEY = '@csreader_settings';
const HIGHLIGHTS_KEY = '@csreader_highlights';

const DEFAULT_SETTINGS: Settings = {
  theme: 'light',
  fontSize: 16,
  pageTransition: 'slide',
};

// --- Settings Storage ---
export async function getSettings(): Promise<Settings> {
  try {
    const data = await AsyncStorage.getItem(SETTINGS_KEY);
    return data ? { ...DEFAULT_SETTINGS, ...JSON.parse(data) } : DEFAULT_SETTINGS;
  } catch (e) {
    console.error('Error reading settings', e);
    return DEFAULT_SETTINGS;
  }
}

export async function saveSettings(settings: Partial<Settings>): Promise<Settings> {
  try {
    const current = await getSettings();
    const updated = { ...current, ...settings };
    await AsyncStorage.setItem(SETTINGS_KEY, JSON.stringify(updated));
    return updated;
  } catch (e) {
    console.error('Error saving settings', e);
    return DEFAULT_SETTINGS;
  }
}

// --- Books Storage ---
export async function getBooks(): Promise<Book[]> {
  try {
    const data = await AsyncStorage.getItem(BOOKS_KEY);
    const books: Book[] = data ? JSON.parse(data) : [];
    // Sort by last read first, then added date
    return books.sort((a, b) => b.lastRead - a.lastRead);
  } catch (e) {
    console.error('Error reading books', e);
    return [];
  }
}

export async function saveBook(book: Book): Promise<Book[]> {
  try {
    const books = await getBooks();
    const index = books.findIndex(b => b.id === book.id);
    if (index > -1) {
      books[index] = book;
    } else {
      books.push(book);
    }
    await AsyncStorage.setItem(BOOKS_KEY, JSON.stringify(books));
    return books;
  } catch (e) {
    console.error('Error saving book', e);
    return [];
  }
}

export async function updateBookProgress(
  id: string,
  progress: number,
  lastLocation: { cfi?: string; page?: number }
): Promise<Book[]> {
  try {
    const books = await getBooks();
    const index = books.findIndex(b => b.id === id);
    if (index > -1) {
      books[index] = {
        ...books[index],
        progress,
        lastCfi: lastLocation.cfi || books[index].lastCfi,
        lastPage: lastLocation.page || books[index].lastPage,
        lastRead: Date.now(),
      };
      await AsyncStorage.setItem(BOOKS_KEY, JSON.stringify(books));
    }
    return books;
  } catch (e) {
    console.error('Error updating book progress', e);
    return [];
  }
}

export async function toggleFavorite(id: string): Promise<Book[]> {
  try {
    const books = await getBooks();
    const index = books.findIndex(b => b.id === id);
    if (index > -1) {
      books[index].favorite = !books[index].favorite;
      await AsyncStorage.setItem(BOOKS_KEY, JSON.stringify(books));
    }
    return books;
  } catch (e) {
    console.error('Error toggling favorite', e);
    return [];
  }
}

export async function deleteBook(id: string): Promise<Book[]> {
  try {
    const books = await getBooks();
    const bookToDelete = books.find(b => b.id === id);

    // Delete file from local FileSystem
    if (bookToDelete && bookToDelete.uri) {
      try {
        const fileInfo = await FileSystem.getInfoAsync(bookToDelete.uri);
        if (fileInfo.exists) {
          await FileSystem.deleteAsync(bookToDelete.uri);
        }
      } catch (err) {
        console.error(`Error deleting file: ${bookToDelete.uri}`, err);
      }
    }

    const filtered = books.filter(b => b.id !== id);
    await AsyncStorage.setItem(BOOKS_KEY, JSON.stringify(filtered));

    // Also delete associated highlights
    const highlights = await getHighlights();
    const filteredHighlights = highlights.filter(h => h.bookId !== id);
    await AsyncStorage.setItem(HIGHLIGHTS_KEY, JSON.stringify(filteredHighlights));

    return filtered;
  } catch (e) {
    console.error('Error deleting book', e);
    return [];
  }
}

// --- Highlights & Notes Storage ---
export async function getHighlights(): Promise<Highlight[]> {
  try {
    const data = await AsyncStorage.getItem(HIGHLIGHTS_KEY);
    return data ? JSON.parse(data) : [];
  } catch (e) {
    console.error('Error reading highlights', e);
    return [];
  }
}

export async function getHighlightsForBook(bookId: string): Promise<Highlight[]> {
  const highlights = await getHighlights();
  return highlights.filter(h => h.bookId === bookId);
}

export async function addHighlight(highlight: Omit<Highlight, 'id' | 'date'>): Promise<Highlight> {
  const newHighlight: Highlight = {
    ...highlight,
    id: `hl_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    date: Date.now(),
  };

  try {
    const highlights = await getHighlights();
    highlights.push(newHighlight);
    await AsyncStorage.setItem(HIGHLIGHTS_KEY, JSON.stringify(highlights));
    return newHighlight;
  } catch (e) {
    console.error('Error adding highlight', e);
    return newHighlight;
  }
}

export async function updateHighlightNote(id: string, note: string): Promise<Highlight[]> {
  try {
    const highlights = await getHighlights();
    const index = highlights.findIndex(h => h.id === id);
    if (index > -1) {
      highlights[index].note = note;
      await AsyncStorage.setItem(HIGHLIGHTS_KEY, JSON.stringify(highlights));
    }
    return highlights;
  } catch (e) {
    console.error('Error updating highlight note', e);
    return [];
  }
}

export async function deleteHighlight(id: string): Promise<Highlight[]> {
  try {
    const highlights = await getHighlights();
    const filtered = highlights.filter(h => h.id !== id);
    await AsyncStorage.setItem(HIGHLIGHTS_KEY, JSON.stringify(filtered));
    return filtered;
  } catch (e) {
    console.error('Error deleting highlight', e);
    return [];
  }
}

export async function scanBooks(): Promise<Book[]> {
  try {
    const books = await getBooks();
    const existingUris = new Set(books.map(b => b.uri));
    const dir = FileSystem.documentDirectory;
    if (!dir) return books;

    const files = await FileSystem.readDirectoryAsync(dir);
    let added = false;

    for (const file of files) {
      const lower = file.toLowerCase();
      if (!lower.endsWith('.epub') && !lower.endsWith('.pdf')) continue;
      const uri = `${dir}${file}`;
      if (existingUris.has(uri)) continue;

      const fileBaseName = file.replace(/\.\w+$/, '');
      let title = fileBaseName;
      let author = 'Bilinmeyen Yazar';

      const dashIndex = fileBaseName.indexOf('-');
      if (dashIndex > 0) {
        title = fileBaseName.substring(0, dashIndex).trim();
        author = fileBaseName.substring(dashIndex + 1).trim();
      }

      const bookType: 'epub' | 'pdf' = lower.endsWith('.epub') ? 'epub' : 'pdf';
      const newBook: Book = {
        id: `book_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
        title,
        author,
        uri,
        type: bookType,
        progress: 0,
        lastCfi: undefined,
        lastPage: undefined,
        lastRead: Date.now(),
        addedDate: Date.now(),
        favorite: false,
      };

      books.push(newBook);
      existingUris.add(uri);
      added = true;
    }

    if (added) {
      await AsyncStorage.setItem(BOOKS_KEY, JSON.stringify(books));
    }

    return books;
  } catch (e) {
    console.error('Error scanning books', e);
    return await getBooks();
  }
}

// Clear all database
export async function clearAllDatabase(): Promise<void> {
  try {
    // List all files in documents directory and delete them
    const dir = FileSystem.documentDirectory;
    if (dir) {
      const files = await FileSystem.readDirectoryAsync(dir);
      for (const file of files) {
        // Keep our reader.html and assets if they are in the document directory
        if (file !== 'reader.html' && file !== 'pdf_viewer.html' && file !== 'epub.min.js' && file !== 'pdf.min.js' && file !== 'pdf.worker.min.js') {
          await FileSystem.deleteAsync(`${dir}${file}`);
        }
      }
    }
    await AsyncStorage.multiRemove([BOOKS_KEY, SETTINGS_KEY, HIGHLIGHTS_KEY]);
  } catch (e) {
    console.error('Error clearing database', e);
  }
}
