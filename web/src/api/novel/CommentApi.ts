import type { Comment1 } from '@/model/Comment';
import type { Page } from '@/model/Page';
import { client } from './client';

const listComment = (params: {
  site: string;
  page: number;
  parentId?: string;
  pageSize: number;
}) => client.get('comment', { searchParams: params }).json<Page<Comment1>>();

const countComment = (params: {
  site: string;
  parentId?: string;
  unique?: number;
  reply?: number;
}) => client.get('comment/count', { searchParams: params }).json<{ total: number }>();

const createComment = (json: {
  site: string;
  parent: string | undefined;
  content: string;
}) => client.post('comment', { json });

const deleteComment = (id: string) => client.delete(`comment/${id}`);
const hideComment = (id: string) => client.put(`comment/${id}/hidden`);
const unhideComment = (id: string) => client.delete(`comment/${id}/hidden`);

export const CommentApi = {
  listComment,
  countComment,
  createComment,
  deleteComment,
  hideComment,
  unhideComment,
};
