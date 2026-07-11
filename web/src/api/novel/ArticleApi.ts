import type {
  Article,
  ArticleCategory,
  ArticleSimplified,
} from '@/model/Article';
import type { Page } from '@/model/Page';
import { client } from './client';

const cleanParams = (obj: Record<string, any>) => {
  const cleaned: Record<string, any> = {};
  for (const key of Object.keys(obj)) {
    const val = obj[key];
    if (val !== undefined && val !== null && val !== '') {
      cleaned[key] = val;
    }
  }
  return cleaned;
};

const listArticle = (params: {
  page: number;
  pageSize: number;
  category?: ArticleCategory;
  author?: string;
  query?: string;
  fuzzyAuthor?: boolean;
  fuzzyTitle?: boolean;
  startAt?: number;
  endAt?: number;
  minViews?: number;
  maxViews?: number;
  minComments?: number;
  maxComments?: number;
  sort?: string;
  sortDesc?: boolean;
}) => {
  const searchParams = cleanParams(params);
  return client
    .get('article', { searchParams })
    .json<Page<ArticleSimplified>>();
};

const getArticle = (id: string) => client.get(`article/${id}`).json<Article>();
const deleteArticle = (id: string) => client.delete(`article/${id}`);
const getArticleAuthors = () => client.get('article/author').json<string[]>();

interface ArticleBody {
  title: string;
  content: string;
  category: ArticleCategory;
}

const createArticle = (json: ArticleBody) =>
  client.post('article', { json }).text();

const updateArticle = (id: string, json: ArticleBody) =>
  client.put(`article/${id}`, { json }).text();

const pinArticle = (id: string) => client.put(`article/${id}/pinned`);
const unpinArticle = (id: string) => client.delete(`article/${id}/pinned`);

const lockArticle = (id: string) => client.put(`article/${id}/locked`);
const unlockArticle = (id: string) => client.delete(`article/${id}/locked`);

const hideArticle = (id: string) => client.put(`article/${id}/hidden`);
const unhideArticle = (id: string) => client.delete(`article/${id}/hidden`);

export const ArticleApi = {
  listArticle,
  getArticleAuthors,

  getArticle,
  createArticle,
  updateArticle,
  deleteArticle,

  pinArticle,
  unpinArticle,
  lockArticle,
  unlockArticle,
  hideArticle,
  unhideArticle,
};
