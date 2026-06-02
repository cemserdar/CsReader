import { Asset } from 'expo-asset';
import { Platform } from 'react-native';
import * as FileSystem from 'expo-file-system/legacy';

// Define the assets we need to copy
const ASSETS_MAP = {
  'reader.html': require('../assets/reader.html'),
  'pdf_viewer.html': require('../assets/pdf_viewer.html'),
  'epub.min.js': require('../assets/epub.min.js.txt'),
  'pdf.min.js': require('../assets/pdf.min.js.txt'),
  'pdf.worker.min.js': require('../assets/pdf.worker.min.js.txt'),
};

export async function prepareReaderAssets(force = false): Promise<void> {
  const docDir = FileSystem.documentDirectory;

  if (!docDir) {
    // On web, documentDirectory is not available, so no local copy is needed.
    return;
  }

  try {
    for (const [filename, moduleSource] of Object.entries(ASSETS_MAP)) {
      const targetUri = `${docDir}${filename}`;

      // Check if file already exists
      if (!force) {
        const fileInfo = await FileSystem.getInfoAsync(targetUri);
        if (fileInfo.exists) {
          // File exists, skip copying (optimization)
          continue;
        }
      }

      // Download/Resolve asset
      const asset = Asset.fromModule(moduleSource);
      await asset.downloadAsync();
      const localUri = asset.localUri || asset.uri;

      if (!localUri) {
        console.warn(`Could not resolve asset URI for ${filename}`);
        continue;
      }

      // Copy to document directory
      await FileSystem.copyAsync({
        from: localUri,
        to: targetUri,
      });
      console.log(`Copied ${filename} to ${targetUri}`);
    }
  } catch (error) {
    console.error('Error preparing reader assets:', error);
    throw error;
  }
}
