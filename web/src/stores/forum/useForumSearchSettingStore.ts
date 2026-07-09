import { useLocalStorage } from '@/util';
import { LSKey } from '../key';

export interface ForumSearchSetting {
  fuzzyTitle: boolean;
  exactAuthor: boolean;
  autoFillToDate: boolean;
}

export const useForumSearchSettingStore = defineStore(LSKey.ForumSearchSetting, () => {
  const setting = useLocalStorage<ForumSearchSetting>(LSKey.ForumSearchSetting, {
    fuzzyTitle: true,
    exactAuthor: true,
    autoFillToDate: false,
  });

  return { setting };
});
