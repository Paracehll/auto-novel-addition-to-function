import { lazy } from '@/util';
import type { DBSchema } from 'idb';
import { openDB } from 'idb';
import type { GlobalGlossaryTerms } from '@/model/GlobalGlossary';

interface GlobalGlossaryCacheDBSchema extends DBSchema {
  'terms-cache': {
    key: string;
    value: GlobalGlossaryTerms;
  };
}

const createDb = lazy(() => {
  return openDB<GlobalGlossaryCacheDBSchema>('global-glossary-cache', 1, {
    upgrade(db, _oldVersion, _newVersion, _transaction, _event) {
      try {
        db.createObjectStore('terms-cache', { keyPath: 'id' });
      } catch (e) {
        console.error(e);
      }
    },
  });
});

export const GlobalGlossaryCacheRepo = {
  get: (id: string): Promise<GlobalGlossaryTerms | undefined> =>
    createDb().then((db) => db.get('terms-cache', id)),
  set: (terms: GlobalGlossaryTerms): Promise<string> =>
    createDb().then((db) => db.put('terms-cache', terms)),
  clear: (): Promise<void> =>
    createDb().then((db) => db.clear('terms-cache')),
};
